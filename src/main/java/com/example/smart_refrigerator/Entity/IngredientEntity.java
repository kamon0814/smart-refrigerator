package com.example.smart_refrigerator.Entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

import javax.validation.constraints.Min;

@Entity
@Data // これでGetter/Setterが自動生成されます
public class IngredientEntity {

    // 主キー
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "食材名を入力してください")
    private String name; // 食材名

    private String category; // カテゴリ（肉、野菜など）

    @Min(value = 0, message = "数量は0以上入力してください")
    @NotNull(message = "数量を入力してください")
    private Double quantity; // 数量

    private String unit; // 単位（個、g、本）

    @FutureOrPresent(message = "賞味期限は今日以降の日付を入力してください")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate expiryDate; // 賞味期限

    // 賞味期限が近いかどうかを判定する便利なメソッド
    public boolean isExpiringSoon() {
        if (this.expiryDate == null)
            return false;
        return this.expiryDate.isBefore(LocalDate.now().plusDays(3));
    }
}