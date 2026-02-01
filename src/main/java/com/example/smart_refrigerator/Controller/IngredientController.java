package com.example.smart_refrigerator.Controller;

import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.time.LocalDateTime;
import java.util.stream.Collectors;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.example.smart_refrigerator.Entity.IngredientEntity;
import com.example.smart_refrigerator.Entity.RecipeEntity;
import com.example.smart_refrigerator.Entity.CategoryEntity;
import com.example.smart_refrigerator.Repository.CategoryRepository;
import com.example.smart_refrigerator.Repository.IngredientRepository;
import com.example.smart_refrigerator.Repository.RecipeRepository;
import com.example.smart_refrigerator.Service.RecipeService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class IngredientController {

    private final IngredientRepository repository; // リポジトリの注入
    private final RecipeService recipeService; // レシピサービスの注入
    private final RecipeRepository recipeRepository;
    private final CategoryRepository categoryRepository;

    // ルートアクセスは在庫一覧へ
    @GetMapping("/")
    public String redirectRoot() {
        return "redirect:/inventory";
    }

    // 食材一覧表示
    @GetMapping("/inventory")
    public String showInventory(Model model, @RequestParam(value = "category", required = false) String category) {
        List<CategoryEntity> categories = categoryRepository.findAllByOrderByNameAsc();
        List<IngredientEntity> ingredients = (category == null || category.isBlank())
                ? repository.findAll()
                : repository.findAllByCategory(category);
        Map<String, String> categoryEmojiMap = categories.stream()
                .filter(cat -> cat.getName() != null)
                .collect(Collectors.toMap(
                        CategoryEntity::getName,
                        cat -> Optional.ofNullable(cat.getEmoji()).orElse(""),
                        (a, b) -> a,
                        LinkedHashMap::new));
        model.addAttribute("ingredients", ingredients);// すべての食材を取得してモデルに追加
        model.addAttribute("categories", categories);
        model.addAttribute("selectedCategory", category == null ? "" : category);
        model.addAttribute("categoryEmojiMap", categoryEmojiMap);
        return "inventory"; // inventory.htmlテンプレートを返す
    }

    // カテゴリ一覧
    @GetMapping("/categories")
    public String showCategories(Model model) {
        model.addAttribute("categories", categoryRepository.findAllByOrderByNameAsc());
        return "categories";
    }

    // カテゴリ追加
    @PostMapping("/categories/add")
    public String addCategory(@RequestParam("name") String name,
            @RequestParam(value = "emoji", required = false) String emoji) {
        if (name != null) {
            String trimmed = name.trim();
            if (!trimmed.isEmpty() && !categoryRepository.existsByName(trimmed)) {
                CategoryEntity entity = new CategoryEntity();
                entity.setName(trimmed);
                if (emoji != null && !emoji.trim().isEmpty()) {
                    entity.setEmoji(emoji.trim());
                }
                categoryRepository.save(entity);
            }
        }
        return "redirect:/categories";
    }

    // カテゴリ削除
    @PostMapping("/categories/delete/{id}")
    public String deleteCategory(@PathVariable Long id) {
        categoryRepository.deleteById(id);
        return "redirect:/categories";
    }

    // フォーム表示
    @GetMapping("/inventory/add")
    public String showAddForm(Model model) {
        setFormModel(model, new IngredientEntity(), false);
        return "add-form"; // add-form.htmlテンプレートを返す
    }

    // 編集フォーム表示
    @GetMapping("/inventory/edit/{id}")
    public String showEditForm(@PathVariable Long id, Model model) {
        IngredientEntity ingredient = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("無効な食材ID: " + id));
        setFormModel(model, ingredient, true);
        return "add-form"; // add-form.htmlテンプレートを返す
    }

    // 新しい食材追加フォームの表示
    @PostMapping("/inventory/add")
    public String addIngredient(@Validated IngredientEntity ingredientEntity, BindingResult result, Model model) {
        if (result.hasErrors()) {
            setFormModel(model, ingredientEntity, false);
            return "add-form"; // エラーがあれば入力画面に戻す
        }
        repository.save(ingredientEntity);
        return "redirect:/inventory"; // 登録後は一覧へリダイレクト
    }

    // 編集処理
    @PostMapping("/inventory/edit/{id}")
    public String updateIngredient(@PathVariable Long id, @Validated IngredientEntity ingredientEntity,
            BindingResult result, Model model) {
        ingredientEntity.setId(id);
        if (result.hasErrors()) {
            setFormModel(model, ingredientEntity, true);
            return "add-form"; // エラーがあれば入力画面に戻す
        }
        repository.save(ingredientEntity);
        return "redirect:/inventory"; // 更新後は一覧へリダイレクト
    }

    // 削除処理
    @PostMapping("/inventory/delete/{id}")
    public String deleteIngredient(@PathVariable Long id) {
        repository.deleteById(id);
        return "redirect:/inventory";
    }

    // AIレシピ取得
    @GetMapping("/recipe")
    public String getRecipe(Model model) {
        List<IngredientEntity> ingredients = repository.findAll();
        List<String> recipes = recipeService.getAiRecipes(ingredients);
        model.addAttribute("recipes", recipes); // Markdown形式の文字列リスト
        model.addAttribute("savedContents", recipeRepository.findAll().stream()
                .map(RecipeEntity::getContent)
                .collect(Collectors.toList()));
        return "recipe-result";
    }

    // レシピ保存
    @PostMapping("/recipe/save")
    public String saveRecipe(String recipe) {
        if (recipe == null || recipe.isBlank()) {
            return "redirect:/recipe";
        }
        RecipeEntity entity = new RecipeEntity();
        entity.setContent(recipe);
        entity.setCreatedAt(LocalDateTime.now());
        recipeRepository.save(entity);
        return "redirect:/recipes";
    }

    // レシピ保存/取消（トグル）
    @PostMapping("/recipe/toggle")
    @ResponseBody
    public Map<String, Object> toggleRecipe(@RequestParam("recipe") String recipe) {
        if (recipe == null || recipe.isBlank()) {
            return Map.of("saved", false, "message", "empty");
        }

        return recipeRepository.findFirstByContent(recipe)
                .map(existing -> {
                    recipeRepository.delete(existing);
                    return Map.<String, Object>of("saved", false);
                })
                .orElseGet(() -> {
                    RecipeEntity entity = new RecipeEntity();
                    entity.setContent(recipe);
                    entity.setCreatedAt(LocalDateTime.now());
                    recipeRepository.save(entity);
                    return Map.<String, Object>of("saved", true);
                });
    }

    // 保存済みレシピ一覧
    @GetMapping("/recipes")
    public String showSavedRecipes(Model model) {
        model.addAttribute("recipes", recipeRepository.findAll());
        return "saved-recipes";
    }

    // 保存済みレシピ削除
    @PostMapping("/recipes/delete/{id}")
    public String deleteSavedRecipe(@PathVariable Long id) {
        recipeRepository.deleteById(id);
        return "redirect:/recipes";
    }

    private void setFormModel(Model model, IngredientEntity ingredientEntity, boolean isEdit) {
        if (model == null) {
            return;
        }
        model.addAttribute("ingredientEntity", ingredientEntity);
        model.addAttribute("categories", categoryRepository.findAllByOrderByNameAsc());
        if (isEdit) {
            model.addAttribute("formTitle", "食材編集フォーム");
            model.addAttribute("submitLabel", "更新");
            model.addAttribute("formAction", "/inventory/edit/" + ingredientEntity.getId());
        } else {
            model.addAttribute("formTitle", "食材追加フォーム");
            model.addAttribute("submitLabel", "追加");
            model.addAttribute("formAction", "/inventory/add");
        }
    }

}
