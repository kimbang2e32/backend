package com.appad.repository;

import com.appad.models.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Integer> {
    List<Transaction> findByUserId(Integer userId);
    List<Transaction> findByStatus(String status);
    List<Transaction> findByTypeAndStatus(String type, String status);
    List<Transaction> findByType(String type);
    boolean existsByUserIdAndTargetIdAndType(Integer userId, Long targetId, String type);
}
