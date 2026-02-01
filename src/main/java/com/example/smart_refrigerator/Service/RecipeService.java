package com.example.smart_refrigerator.Service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import com.example.smart_refrigerator.Entity.IngredientEntity;

import lombok.RequiredArgsConstructor;

// レシピ生成サービス
@Service
@RequiredArgsConstructor
public class RecipeService {

    private final ChatClient chatClient; // Spring AIの主要インターフェース

    // 食材リストからAIレシピを取得
    public String getAiRecipe(List<IngredientEntity> ingredients) {
        return getAiRecipes(ingredients).stream().findFirst().orElse("");
    }

    // 食材リストからAIレシピを複数取得
    public List<String> getAiRecipes(List<IngredientEntity> ingredients) {
        if (ingredients == null || ingredients.isEmpty()) {
            return List.of("冷蔵庫に食材が登録されていません。まずは食材を追加してください。");
        }

        // 食材の「名前」「数量」「単位」「期限」をひとまとめの文字列にする
        String ingredientDetails = ingredients.stream()
                .map(ingredient -> String.format("- %s: %s  %s %s (賞味期限: %s)",
                        ingredient.getName(),
                        ingredient.getCategory(),
                        ingredient.getQuantity(),
                        ingredient.getUnit(),
                        ingredient.getExpiryDate()))
                .collect(Collectors.joining("\n"));

        String response = callAi(buildPrompt(ingredientDetails, false));

        List<String> recipes = parseRecipes(response);

        if (!recipes.isEmpty() && recipes.stream().allMatch(this::hasRequiredSections)) {
            return limitRecipes(recipes);
        }

        // 不完全な場合は1回だけ厳しめのプロンプトで再生成
        String retryResponse = callAi(buildPrompt(ingredientDetails, true));
        List<String> retryRecipes = parseRecipes(retryResponse);
        if (!retryRecipes.isEmpty()) {
            return limitRecipes(retryRecipes);
        }

        return List.of(response);
    }

    private String buildPrompt(String ingredientDetails, boolean strict) {
        StringBuilder sb = new StringBuilder();

        // 役割の定義
        sb.append("あなたはプロの料理研究家であり、冷蔵庫管理アプリのパーソナルシェフです。\n");
        sb.append("提供された食材リストを分析し、ユーザーが「今、あるもので」作れる最高に美味しいレシピを2つ提案してください。\n\n");

        // インプット情報
        sb.append("### 現在の冷蔵庫在庫状況\n");
        sb.append(ingredientDetails).append("\n\n");

        // 基本ルール
        sb.append("### レシピ作成の基本ルール\n");
        sb.append("1. **在庫優先消費**: 賞味期限が近い順に必ず1つ以上のメイン食材として組み込んでください。\n");
        sb.append("2. **分量厳守**: 提示された数量（個、丁、パック等）を超えないレシピにしてください。2〜3人分を基準とします。\n");
        sb.append("3. **整数表記**: 材料の数量は小数や分数を使わず、整数で記載してください。\n");
        sb.append("4. **調味料の扱い**: 塩、醤油、油などの一般的な基本調味料は、在庫リストになくても「家にあるもの」として少量使用して構いません。\n\n");

        // フォーマット指定
        sb.append("### 出力フォーマット（Markdown）\n");
        sb.append("回答は必ず以下の構造を守り、2つのレシピの間は `---` で区切ってください。\n\n");
        sb.append("# 料理名\n");
        sb.append("> **消費のポイント**: 賞味期限が近い食材を使い切る工夫を1行で記述\n\n");
        sb.append("## 材料（2〜3人分）\n");
        sb.append("- 食材名: 必要量（在庫 在庫量単位 → 残り 計算後の残り単位）\n");
        sb.append("- 基本調味料: 必要量 (家庭にあるもの)\n\n");
        sb.append("## 調理手順\n");
        sb.append("1. **工程名**\n   - 具体的な手順・火加減・時間の詳細を記述\n");
        sb.append("2. **工程名**\n   - 具体的な手順・火加減・時間の詳細を記述\n\n");
        sb.append("## シェフのアドバイスと保存\n");
        sb.append("- **コツ**: 美味しく作るためのワンポイント\n");
        sb.append("- **保存**: 冷蔵/冷凍での保存可能日数と再加熱の注意点\n");

        if (strict) {
            sb.append("\n\n【警告】出力は絶対に途中で省略しないでください。「手順」は完成まで、1つひとつの工程を具体的に書ききってください。");
            sb.append("「ポイントと保存」セクションがない回答は認められません。");
        }

        return sb.toString();
    }

    private String callAi(String prompt) {
        return chatClient.prompt()
                .user(prompt)
                .call()
                .content();
    }

    private List<String> parseRecipes(String response) {
        if (response == null || response.isBlank()) {
            return List.of();
        }

        return Arrays.stream(response.split("(?m)^---\\s*$"))
                .map(String::trim)
                .filter(text -> !text.isBlank())
                .map(this::normalizeRecipeMarkdown)
                .filter(text -> !text.isBlank())
                .toList();
    }

    private List<String> limitRecipes(List<String> recipes) {
        if (recipes.size() > 2) {
            return recipes.subList(0, 2);
        }
        return recipes;
    }

    private boolean hasRequiredSections(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return false;
        }
        String text = markdown;
        return text.contains("#")
                && text.contains("## 材料")
                && text.contains("## 手順")
                && text.contains("## ポイントと保存");
    }

    private String normalizeRecipeMarkdown(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }

        // 先頭に説明文がある場合は、最初の見出しから切り出す
        int headingIndex = markdown.indexOf("#");
        if (headingIndex > 0) {
            return markdown.substring(headingIndex).trim();
        }

        // 見出しが無い説明だけの段落は除外
        boolean hasHeading = markdown.lines().anyMatch(line -> line.trim().startsWith("#"));
        if (!hasHeading && markdown.length() < 120) {
            return "";
        }

        return markdown.trim();
    }
}