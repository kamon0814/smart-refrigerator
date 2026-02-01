package com.example.smart_refrigerator.Repository;

import java.util.List;

import com.example.smart_refrigerator.Entity.IngredientEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IngredientRepository extends JpaRepository<IngredientEntity, Long> {
    List<IngredientEntity> findAllByCategory(String category);
}