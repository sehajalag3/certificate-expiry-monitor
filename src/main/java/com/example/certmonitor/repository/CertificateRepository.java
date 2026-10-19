package com.example.certmonitor.repository;

import com.example.certmonitor.domain.Certificate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface CertificateRepository extends JpaRepository<Certificate, Long>, JpaSpecificationExecutor<Certificate> {

    long countByExpiryDateBefore(LocalDate date);

    long countByExpiryDateBetween(LocalDate start, LocalDate end);

    List<Certificate> findByExpiryDateLessThanEqualOrderByExpiryDateAsc(LocalDate date);
}
