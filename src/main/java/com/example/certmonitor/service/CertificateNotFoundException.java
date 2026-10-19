package com.example.certmonitor.service;

public class CertificateNotFoundException extends RuntimeException {

    public CertificateNotFoundException(Long id) {
        super("Certificate not found with id " + id);
    }
}
