package com.ajtbackend.config;

import com.ajtbackend.model.Passageiro;
import com.ajtbackend.repository.PassageiroRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Preenche o documento_hash (V12) dos passageiros gravados antes dele existir.
 * Nao da pra fazer na migracao: o hash sai do documento decifrado, e so a API tem a chave.
 * Roda a cada subida, mas depois da primeira nao acha mais nada sem hash.
 */
@Component
@RequiredArgsConstructor
public class DocumentoHashBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DocumentoHashBackfill.class);

    private final PassageiroRepository passageiroRepository;
    private final DocumentoHash documentoHash;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Passageiro> semHash = passageiroRepository.findByDocumentoHashIsNull();
        semHash.forEach(p -> p.setDocumentoHash(documentoHash.calcular(p.getTipoDocumento(), p.getDocumento())));
        if (!semHash.isEmpty()) {
            log.info("documento_hash preenchido em {} passageiro(s) antigos", semHash.size());
        }
    }
}
