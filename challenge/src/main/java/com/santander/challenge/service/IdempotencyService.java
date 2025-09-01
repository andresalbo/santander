package com.santander.challenge.service;

import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.santander.challenge.model.EntidadBancaria;
import com.santander.challenge.model.IdempotencyKey;
import com.santander.challenge.repository.EntidadBancariaRepository;
import com.santander.challenge.repository.IdempotencyKeyRepository;
import org.springframework.retry.backoff.*;
import jakarta.persistence.OptimisticLockException;

@Service
public class IdempotencyService {

	private final IdempotencyKeyRepository repository;
	private final EntidadBancariaRepository repositoryEB;

	public IdempotencyService(IdempotencyKeyRepository repository, EntidadBancariaRepository repositoryEB) {
		this.repository = repository;
		this.repositoryEB = repositoryEB;
	}

	@Transactional
	public boolean checkAndSaveKey(String key) {
		try {
            // Intentar guardar directamente, sin buscar antes
            IdempotencyKey entity = new IdempotencyKey();
            entity.setKeyValue(key);
            repository.save(entity);
            return true;
        } catch (DataIntegrityViolationException ex) {
            // Si hay constraint UNIQUE en key_value => significa que otro thread ya lo insertó
            return false;
        }
	}

	/**
     * Procesa la entidad con manejo de concurrencia optimista.
     * Si otro thread modifica al mismo tiempo, se reintenta hasta 3 veces.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public EntidadBancaria processEntity(String id) {
        UUID uuidObject = UUID.fromString(id);

        EntidadBancaria eb = repositoryEB.findById(uuidObject)
                .orElseThrow(() -> new IllegalArgumentException("Entidad no encontrada"));

        if (uuidObject.equals(eb.getId())) {
            throw new IllegalStateException("Already processed");
        }

        // ejemplo: marcar como procesado
        eb.setNombre(eb.getNombre() + " - procesado");
        repositoryEB.saveAndFlush(eb);

        return eb;
    }
    

    /**
     * Si después de 3 reintentos no se pudo guardar, se ejecuta este método.
     */
    @Recover
    public EntidadBancaria recover(OptimisticLockException e, String id) {
        throw new IllegalStateException("No se pudo procesar la entidad por concurrencia: " + id, e);
    }
}