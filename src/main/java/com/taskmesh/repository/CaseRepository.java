package com.taskmesh.repository;

import com.taskmesh.model.Case;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaseRepository extends JpaRepository<Case, String> {
}
