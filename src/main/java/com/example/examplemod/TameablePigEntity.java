package com.example.examplemod;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import com.example.examplemod.ai.ArcherGoal;
import com.example.examplemod.ai.ResourceGatherGoal;
import com.example.examplemod.mutation.MutationDefinition;
import com.example.examplemod.mutation.Mutations;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.Items;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;

public class TameablePigEntity extends TamableAnimal implements RangedAttackMob {

    // Ключи синхронизации данных между Сервером и Клиентом
    private static final EntityDataAccessor<Integer> DATA_LEVEL = SynchedEntityData.defineId(TameablePigEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_EXP = SynchedEntityData.defineId(TameablePigEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SKILL_POINTS = SynchedEntityData.defineId(TameablePigEntity.class, EntityDataSerializers.INT);
    // Мутации свиньи: id мутации -> уровень, целиком лежат в одном CompoundTag.
    // Это проще, чем заводить фиксированный набор синхронизируемых полей под каждую мутацию,
    // и легко расширяется, когда появится реальный список мутаций.
    private static final EntityDataAccessor<CompoundTag> DATA_MUTATIONS = SynchedEntityData.defineId(TameablePigEntity.class, EntityDataSerializers.COMPOUND_TAG);

    // Модификаторы атрибутов для динамического увеличения ХП и Урона
    private static final ResourceLocation HEALTH_BONUS_ID = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "pig_health_bonus");
    private static final ResourceLocation DAMAGE_BONUS_ID = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "pig_damage_bonus");

    public static final int MAX_LEVEL = 100;

    private final SimpleContainer inventory = new SimpleContainer(4);

    public SimpleContainer getInventory() {
        return this.inventory;
    }

    public TameablePigEntity(EntityType<? extends TamableAnimal> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier createAttributes() {
        return TamableAnimal.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)       // Базовое ХП: 20 (10 сердечек)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)     
                .add(Attributes.ATTACK_DAMAGE, 4.0D)      // Базовый урон: 4
                .build();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_LEVEL, 1);
        builder.define(DATA_EXP, 0);
        builder.define(DATA_SKILL_POINTS, 0);
        builder.define(DATA_MUTATIONS, new CompoundTag());
    }

    // --- ГЕТТЕРЫ И СЕТТЕРЫ ПАРАМЕТРОВ ---

    public int getPigLevel() {
        return this.entityData.get(DATA_LEVEL);
    }

    public void setPigLevel(int level) {
        // Не даём уровню превысить потолок, даже если кто-то передаст сюда число больше MAX_LEVEL
        // (например, при загрузке старого сохранения или командой).
        this.entityData.set(DATA_LEVEL, Math.min(level, MAX_LEVEL));
        this.updateStatsForLevel(); // Пересчитываем ХП и урон под новый уровень
    }

    public int getPigExp() {
        return this.entityData.get(DATA_EXP);
    }

    public int getSkillPoints() {
        return this.entityData.get(DATA_SKILL_POINTS);
    }

    public void setSkillPoints(int points) {
        this.entityData.set(DATA_SKILL_POINTS, points);
    }

    // --- ОБЩИЙ ХЕЛПЕР ДЛЯ МУТАЦИЙ-АТРИБУТОВ (здоровье/броня/урон/скорость и т.п.) ---
    // Используется из эффектов в Mutations.java — каждая такая мутация просто ставит/убирает
    // свой именованный модификатор на нужном атрибуте, не трогая базовые значения и другие бонусы
    // (например, бонус от уровня самой свиньи, который считается отдельно в updateStatsForLevel).

    /**
     * Выставляет (или убирает при value == 0) именованный модификатор на атрибуте свиньи.
     * modifierId должен быть уникальным на мутацию, иначе разные мутации будут затирать друг друга.
     */
    public void setAttributeBonus(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
                                   ResourceLocation modifierId, double value, AttributeModifier.Operation operation) {
        AttributeInstance instance = this.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        instance.removeModifier(modifierId);
        if (value != 0) {
            instance.addTransientModifier(new AttributeModifier(modifierId, value, operation));
        }
    }

    // --- МОРКОСВИНЬЯ ---
    // На хвосте отрастает морковка (визуально — пока просто заглушка без своей модели/рендера,
    // это отдельная арт-задача), которую владелец может "съесть" ПКМ пустой рукой, с перезарядкой.
    // Кулдаун храним в тиках и сохраняем в NBT, чтобы не обнулялся при выходе из игры.
    private int carrotCooldown = 0;

    public boolean isCarrotReady() {
        return this.hasMutation("carrot_pig") && this.carrotCooldown <= 0;
    }

    public int getCarrotCooldown() {
        return this.carrotCooldown;
    }

    /**
     * Пытается "съесть морковку с хвоста". Вызывается из mobInteract() при ПКМ пустой рукой.
     * @return true, если сработало (мутация есть и не на кулдауне) — тогда клик нужно считать обработанным
     */
    public boolean tryEatCarrot(Player player) {
        if (!this.isCarrotReady()) {
            return false;
        }
        int level = this.getMutationLevel("carrot_pig");
        // Эффект как от золотой морковки, масштабируется с уровнем мутации
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.REGENERATION, 60 + level * 20, Math.min(1, level / 3)));
        player.getFoodData().eat(2 + level, 0.2F);
        this.carrotCooldown = carrotCooldownTicks(level);
        this.level().broadcastEntityEvent(this, (byte) 7);
        return true;
    }

    /** Кулдаун Моркосвиньи по уровню мутации: от ~60 сек на Lvl1 до ~10 сек на Lvl5 (макс). */
    public static int carrotCooldownTicks(int level) {
        return Math.max(200, 1200 - level * 200);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();

        if (this.carrotCooldown > 0) {
            this.carrotCooldown--;
        }

        // --- Регенерация ---
        if (this.hasMutation("regeneration") && this.getHealth() < this.getMaxHealth()) {
            int level = this.getMutationLevel("regeneration");
            int interval = regenIntervalTicks(level);
            if (interval > 0 && this.tickCount % interval == 0) {
                this.heal(1.0F);
            }
        }

        tickTier2Mutations();
        tickCourier();
        tickFisher();
    }

    /** Интервал (в тиках) между тиками пассивной регенерации по уровню мутации: быстрее на высоких уровнях. */
    public static int regenIntervalTicks(int level) {
        return Math.max(20, 100 - level * 15);
    }

    // --- TIER 2: РАБОЧИЕ ПРОФЕССИИ И КУРЬЕР ---

    /**
     * Внутренний буфер для добычи "рабочих" мутаций (Лесоруб/Фермер/Шахтёр/Рыбак) — переиспользуем
     * уже существующий this.inventory (4 слота), который раньше был для слотов-улучшений в UI и с тех
     * пор просто не использовался. Курьер разгружает этот буфер в привязанный сундук; без Курьера,
     * когда буфер заполнен, лишние предметы просто падают под ноги свинье, как обычный дроп блока.
     */
    public void storeOrDropItem(ItemStack stack) {
        ItemStack leftover = tryInsert(this.inventory, stack);
        if (!leftover.isEmpty()) {
            this.spawnAtLocation(leftover);
        }
    }

    private static ItemStack tryInsert(Container container, ItemStack stack) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        stack = stack.copy();
        for (int i = 0; i < container.getContainerSize() && !stack.isEmpty(); i++) {
            ItemStack slot = container.getItem(i);
            if (slot.isEmpty()) {
                container.setItem(i, stack);
                stack = ItemStack.EMPTY;
            } else if (ItemStack.isSameItemSameComponents(slot, stack)) {
                int room = slot.getMaxStackSize() - slot.getCount();
                int move = Math.min(room, stack.getCount());
                if (move > 0) {
                    slot.grow(move);
                    stack.shrink(move);
                    container.setChanged();
                }
            }
        }
        return stack;
    }

    // --- Курьер: привязанный сундук (задаётся Посохом Курьера, см. CourierStaffItem) ---
    private BlockPos courierChestPos;

    public void setCourierChestPos(BlockPos pos) {
        this.courierChestPos = pos;
    }

    public BlockPos getCourierChestPos() {
        return this.courierChestPos;
    }

    private void tickCourier() {
        if (!this.hasMutation("courier") || this.courierChestPos == null) {
            return;
        }
        if (this.tickCount % 100 != 0) { // раз в 5 сек — не нужно чаще, это не хопер
            return;
        }
        if (!(this.level() instanceof ServerLevel serverLevel) || !serverLevel.isLoaded(this.courierChestPos)) {
            return;
        }
        if (!(serverLevel.getBlockEntity(this.courierChestPos) instanceof Container chest)) {
            return; // сундук сломали/переставили — просто ждём, буфер тем временем копится (или роняется, если полон)
        }
        for (int i = 0; i < this.inventory.getContainerSize(); i++) {
            ItemStack stack = this.inventory.getItem(i);
            if (!stack.isEmpty()) {
                this.inventory.setItem(i, tryInsert(chest, stack));
            }
        }
    }

    // --- Рыбак: сильно упрощённая механика (без настоящей мини-игры рыбалки) — раз в ~30 сек,
    // если свинья стоит у воды, "ловит" случайную рыбу прямо в буфер (storeOrDropItem). ---
    private void tickFisher() {
        if (!this.hasMutation("fisher")) {
            return;
        }
        int level = this.getMutationLevel("fisher");
        int interval = fisherIntervalTicks(level); // раньше был фиксированный 600 — теперь зависит от уровня
        if (this.tickCount % interval != 0) {
            return;
        }
        if (!this.level().getFluidState(this.blockPosition()).is(FluidTags.WATER)
                && !this.level().getFluidState(this.blockPosition().below()).is(FluidTags.WATER)) {
            return;
        }
        ItemStack fish = switch (this.random.nextInt(10)) {
            case 0 -> new ItemStack(Items.PUFFERFISH);
            case 1, 2 -> new ItemStack(Items.TROPICAL_FISH);
            case 3, 4, 5 -> new ItemStack(Items.SALMON);
            default -> new ItemStack(Items.COD);
        };
        this.storeOrDropItem(fish);
        this.addPigExp(2);
    }

    /** Интервал ловли рыбы: от 600 тиков (30с) на Lvl1 до 100 тиков (5с) на Lvl10 (макс). */
    public static int fisherIntervalTicks(int level) {
        return Math.max(100, 600 - level * 50);
    }

    // --- Лучник: свинья реализует RangedAttackMob, стреляет как скелет (см. ArcherGoal) ---
    @Override
    public void performRangedAttack(net.minecraft.world.entity.LivingEntity target, float velocity) {
        Arrow arrow = new Arrow(this.level(), this, ItemStack.EMPTY, null);
        double dx = target.getX() - this.getX();
        double dy = target.getY(0.3333) - arrow.getY();
        double dz = target.getZ() - this.getZ();
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        // Разброс (inaccuracy) уменьшается с уровнем мутации "archer" — точнее стреляет на высоких уровнях
        float inaccuracy = Math.max(1.0F, 8.0F - this.getMutationLevel("archer"));
        arrow.shoot(dx, dy + horizontalDist * 0.2, dz, 1.6F, inaccuracy);
        this.playSound(net.minecraft.sounds.SoundEvents.ARROW_SHOOT, 1.0F, 1.0F / (this.random.nextFloat() * 0.4F + 0.8F));
        this.level().addFreshEntity(arrow);
    }
    // --- TIER 2: иммунитеты и пассивные баффы ---
    // Все эти мутации бинарные (maxLevel небольшой, эффект по сути on/off, кроме "Плавания" —
    // там уровень влияет на силу Прыти Дельфина).

    private void tickTier2Mutations() {
        // Сверхдыхание — держим запас воздуха полным, поэтому свинья физически не может утонуть
        if (this.hasMutation("overbreathing")) {
            this.setAirSupply(this.getMaxAirSupply());
        }

        // Плавание — Прыть Дельфина, пока свинья в воде (быстрое плавание + без затруднений)
        if (this.hasMutation("aquatic") && this.isInWater()) {
            int level = this.getMutationLevel("aquatic");
            this.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.DOLPHINS_GRACE, 60, Math.max(0, level - 1), false, false));
        }

        // Планирование — гасим скорость падения, имитируя парение (курица-стиль)
        if (this.hasMutation("gliding") && !this.onGround() && this.getDeltaMovement().y < -0.1) {
            this.setDeltaMovement(this.getDeltaMovement().multiply(1.0, 0.6, 1.0));
        }
    }

    @Override
    public boolean fireImmune() {
        // Нерушимый — полный иммунитет к огню/лаве/магме (fireImmune покрывает урон с тегом IS_FIRE,
        // куда входит и лава).
        return super.fireImmune() || this.hasMutation("fire_immunity");
    }

    @Override
    public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source) {
        // Сверхдыхание — иммунитет к утоплению и удушению в блоках.
        if (this.hasMutation("overbreathing")
                && (source.is(net.minecraft.world.damagesource.DamageTypes.DROWN)
                || source.is(net.minecraft.world.damagesource.DamageTypes.IN_WALL))) {
            return true;
        }
        return super.isInvulnerableTo(source);
    }

    @Override
    public boolean canBeAffected(net.minecraft.world.effect.MobEffectInstance effectInstance) {
        // Зельеустойчивость — яд и иссушение просто не накладываются (не только не наносят урон,
        // но и не висят иконкой), "магия" в широком смысле уже покрывается урон-иммунитетом ниже
        // не требуется отдельно, т.к. большая часть "магического" урона в ваниле идёт через эти же эффекты.
        if (this.hasMutation("potion_resistance")
                && (effectInstance.is(net.minecraft.world.effect.MobEffects.POISON)
                || effectInstance.is(net.minecraft.world.effect.MobEffects.WITHER))) {
            return false;
        }
        return super.canBeAffected(effectInstance);
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, net.minecraft.world.damagesource.DamageSource source) {
        // Планирование — без урона от падения (парит, как курица)
        if (this.hasMutation("gliding")) {
            return false;
        }
        return super.causeFallDamage(fallDistance, multiplier, source);
    }

    // --- МУТАЦИИ ---
    // Разблокировка — через Генетический Инжектор (Пробирка с Мутацией), появится на этапе "Предметы".
    // Прокачка — тратит DATA_SKILL_POINTS (те самые очки развития от левелапа), Вкладка 2 GUI дёрнет upgradeMutation().

    /** Текущий уровень мутации у свиньи (0, если ещё не введена). */
    public int getMutationLevel(String mutationId) {
        return this.entityData.get(DATA_MUTATIONS).getInt(mutationId);
    }

    /** Введена ли свинье данная мутация (хотя бы 1-й уровень). */
    public boolean hasMutation(String mutationId) {
        return this.getMutationLevel(mutationId) > 0;
    }

    /** Снимок всех мутаций свиньи (id -> уровень) — для отрисовки списка на Вкладке 2. */
    public Map<String, Integer> getMutations() {
        CompoundTag tag = this.entityData.get(DATA_MUTATIONS);
        Map<String, Integer> result = new LinkedHashMap<>();
        for (String key : tag.getAllKeys()) {
            result.put(key, tag.getInt(key));
        }
        return result;
    }

    private void setMutationLevel(String mutationId, int level) {
        // entityData хранит неизменяемый снимок CompoundTag — редактируем копию и кладём обратно,
        // иначе синхронизация на клиент может не заметить изменение.
        CompoundTag tag = this.entityData.get(DATA_MUTATIONS).copy();
        if (level <= 0) {
            tag.remove(mutationId);
        } else {
            tag.putInt(mutationId, level);
        }
        this.entityData.set(DATA_MUTATIONS, tag);

        MutationDefinition definition = Mutations.get(mutationId);
        if (definition != null) {
            definition.applyEffect(this, level);
        }
    }

    /**
     * Вживляет свинье новую мутацию (вызывается Генетическим Инжектором при использовании
     * Пробирки с Мутацией). Если мутация уже есть — ничего не делает: повторное введение той
     * же ДНК не имеет смысла, прокачка идёт отдельно через очки развития.
     *
     * @return true, если мутация успешно разблокирована (появилась на уровне 1)
     */
    public boolean unlockMutation(String mutationId) {
        if (!Mutations.exists(mutationId) || this.hasMutation(mutationId)) {
            return false;
        }
        this.setMutationLevel(mutationId, 1);
        return true;
    }

    /** Стоимость прокачки мутации на следующий уровень, в очках развития. -1, если мутация неизвестна. */
    public int getMutationUpgradeCost(String mutationId) {
        MutationDefinition definition = Mutations.get(mutationId);
        if (definition == null) {
            return -1;
        }
        return definition.getUpgradeCost(this.getMutationLevel(mutationId));
    }

    /**
     * Прокачивает мутацию на 1 уровень за очки развития свиньи.
     * @return true, если прокачка прошла (мутация введена, не макс. уровень, хватило очков)
     */
    public boolean upgradeMutation(String mutationId) {
        MutationDefinition definition = Mutations.get(mutationId);
        if (definition == null) {
            return false;
        }
        int currentLevel = this.getMutationLevel(mutationId);
        if (currentLevel <= 0 || currentLevel >= definition.getMaxLevel()) {
            return false;
        }
        int cost = definition.getUpgradeCost(currentLevel);
        if (this.getSkillPoints() < cost) {
            return false;
        }
        this.setSkillPoints(this.getSkillPoints() - cost);
        this.setMutationLevel(mutationId, currentLevel + 1);
        return true;
    }

    /**
     * Полностью убирает мутацию у свиньи (кнопка "удалить" на Вкладке 2 GUI).
     * Потраченные на прокачку очки НЕ возвращаются — иначе билд можно было бы бесплатно
     * бесконечно "перекатывать". Если нужен частичный возврат очков — легко добавить здесь.
     */
    public boolean removeMutation(String mutationId) {
        if (!this.hasMutation(mutationId)) {
            return false;
        }
        this.setMutationLevel(mutationId, 0);
        return true;
    }

    /** Пересчитывает эффекты всех введённых мутаций — нужно после загрузки сохранения мира. */
    private void applyAllMutationEffects() {
        for (Map.Entry<String, Integer> entry : this.getMutations().entrySet()) {
            MutationDefinition definition = Mutations.get(entry.getKey());
            if (definition != null) {
                definition.applyEffect(this, entry.getValue());
            }
        }
    }

    // Добавление опыта и проверка на повышение уровня
    public void addPigExp(int amount) {
        // Свинья уже на максимальном уровне — опыт больше не нужен, не копим его.
        if (this.getPigLevel() >= MAX_LEVEL) {
            return;
        }

        int newExp = this.getPigExp() + amount;
        int expForNextLevel = this.getPigLevel() * 15; // Для каждого уровня нужно всё больше опыта

        while (newExp >= expForNextLevel && this.getPigLevel() < MAX_LEVEL) {
            newExp -= expForNextLevel;
            this.setPigLevel(this.getPigLevel() + 1);
            this.setSkillPoints(this.getSkillPoints() + 1); // Даем 1 очко талантов
            expForNextLevel = this.getPigLevel() * 15;

            if (!this.level().isClientSide) {
                this.level().broadcastEntityEvent(this, (byte) 7); // Зеленые частицы / сердечки
                
                // Сообщение хозяину в чат
                if (this.getOwner() instanceof Player player) {
                    player.sendSystemMessage(Component.literal("§a[Pigs] §fТвой боевой хряк поднял уровень до §e" + this.getPigLevel() + "§f! Доступно очков талантов: §b" + this.getSkillPoints()));
                }
            }
        }
        // Если уровень уперся в потолок, остаток опыта не хранит смысла — обнуляем, чтобы не копился зря.
        this.entityData.set(DATA_EXP, this.getPigLevel() >= MAX_LEVEL ? 0 : newExp);
    }

    // Динамический перерасчет характеристик (+2 ХП и +0.5 урона за каждый уровень)
    private void updateStatsForLevel() {
        int level = this.getPigLevel() - 1; // Бонусы со 2-го уровня

        AttributeInstance healthAttr = this.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttr != null) {
            healthAttr.removeModifier(HEALTH_BONUS_ID);
            if (level > 0) {
                healthAttr.addTransientModifier(new AttributeModifier(HEALTH_BONUS_ID, level * 2.0D, AttributeModifier.Operation.ADD_VALUE));
            }
        }

        AttributeInstance damageAttr = this.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damageAttr != null) {
            damageAttr.removeModifier(DAMAGE_BONUS_ID);
            if (level > 0) {
                damageAttr.addTransientModifier(new AttributeModifier(DAMAGE_BONUS_ID, level * 0.5D, AttributeModifier.Operation.ADD_VALUE));
            }
        }
    }

    // --- ИИ И ПОВЕДЕНИЕ ---

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(ModItems.PIG_TREAT.get());
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, new SitWhenOrderedToGoal(this));
        this.goalSelector.addGoal(3, new FollowOwnerGoal(this, 1.25D, 10.0F, 2.0F));
        this.goalSelector.addGoal(4, new ArcherGoal(this)); // приоритет выше ближнего боя — если archer не разблокирован, canUse() вернёт false и ход перейдёт к MeleeAttackGoal
        this.goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.2D, true));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        // Рабочие профессии Tier 2 — добавлены всегда, но внутри сами проверяют hasMutation(...)
        // и просто не активируются, если мутации нет. Виртуальный инструмент нужен только для
        // расчёта дропа (Block.getDrops), физически в лапах/зубах свиньи не отображается.
        // Параметры: (мутация, что искать, инструмент, пересаживать?, рубить дерево целиком?,
        //             умеет копать препятствия?, множитель скорости добычи)
        this.goalSelector.addGoal(4, new ResourceGatherGoal(this, "lumberjack",
                state -> state.is(BlockTags.LOGS), new ItemStack(Items.DIAMOND_AXE),
                false, true, false, 1.6D)); // рубит всё дерево целиком, поэтому медленнее (x1.6 времени на блок)
        this.goalSelector.addGoal(4, new ResourceGatherGoal(this, "miner",
                TameablePigEntity::isOre, new ItemStack(Items.DIAMOND_PICKAXE),
                false, false, true, 1.0D)); // умеет прорывать путь через камень к руде
        this.goalSelector.addGoal(4, new ResourceGatherGoal(this, "farmer",
                state -> state.getBlock() instanceof net.minecraft.world.level.block.CropBlock crop && crop.isMaxAge(state),
                new ItemStack(Items.DIAMOND_HOE), true, false, false, 1.0D));

        this.targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
        this.targetSelector.addGoal(3, new HurtByTargetGoal(this));
    }

    private static boolean isOre(net.minecraft.world.level.block.state.BlockState state) {
        return state.is(BlockTags.COAL_ORES) || state.is(BlockTags.IRON_ORES) || state.is(BlockTags.COPPER_ORES)
                || state.is(BlockTags.GOLD_ORES) || state.is(BlockTags.REDSTONE_ORES) || state.is(BlockTags.LAPIS_ORES)
                || state.is(BlockTags.DIAMOND_ORES) || state.is(BlockTags.EMERALD_ORES);
    }

    // Получение опыта за убийство мобов
    @Override
    public void awardKillScore(Entity killed, int score, net.minecraft.world.damagesource.DamageSource damageSource) {
        super.awardKillScore(killed, score, damageSource);
        if (this.isTame()) {
            this.addPigExp(10); // +10 опыта за победу над мобом
        }
    }

    // Взаимодействие
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);

        // Если свинья УЖЕ приручена и хозяин кликает по ней
        if (this.isTame() && this.isOwnedBy(player)) {
            
            // Посох Курьера — если у него есть привязанный сундук, переносим привязку на свинью
            // (работает независимо от того, есть ли уже мутация "courier" — привязать можно заранее)
            if (itemstack.getItem() instanceof CourierStaffItem) {
                BlockPos chestPos = CourierStaffItem.getBoundChest(itemstack);
                if (chestPos != null) {
                    if (!this.level().isClientSide) {
                        this.setCourierChestPos(chestPos);
                        player.sendSystemMessage(Component.literal("§b[Pigs] §fСвинья привязана к сундуку курьера."));
                    }
                    return InteractionResult.sidedSuccess(this.level().isClientSide);
                }
            }

            // Генетический Инжектор — если заряжен мутацией, вводит её свинье и разряжается.
            // applyToPig() сам решает, обработал ли он клик (пустой инжектор -> false, идём дальше).
            if (itemstack.getItem() instanceof GeneticInjectorItem
                    && GeneticInjectorItem.applyToPig(itemstack, this, player)) {
                return InteractionResult.sidedSuccess(this.level().isClientSide);
            }

            // Если в руке Деликатес — лечим свинью и даем ей опыт!
            if (itemstack.is(ModItems.PIG_TREAT.get())) {
                if (this.getHealth() < this.getMaxHealth() || this.getPigLevel() < MAX_LEVEL) {
                    if (!player.getAbilities().instabuild) {
                        itemstack.shrink(1);
                    }
                    this.heal(5.0F); // Восстанавливает 2.5 сердечка
                    this.addPigExp(5); // Кормление даёт +5 опыта
                    this.level().broadcastEntityEvent(this, (byte) 7);
                    return InteractionResult.sidedSuccess(this.level().isClientSide);
                }
            }

            // Если игрок зажал Shift + ПКМ пустой рукой — показываем инфо в чат
            // Если игрок зажал Shift + ПКМ — открываем GUI Свиньи!
    if (player.isShiftKeyDown() && hand == InteractionHand.MAIN_HAND) {
        if (!this.level().isClientSide) {
            player.openMenu(new SimpleMenuProvider(
                (containerId, playerInv, p) -> new PigMenu(containerId, playerInv, this),
                Component.literal("Инвентарь Свиньи")
            ), buf -> buf.writeInt(this.getId()));
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

            // Моркосвинья — ПКМ пустой рукой "съедает" морковку с хвоста (если мутация есть и не на
            // кулдауне). Если кулдаун ещё не прошёл — просто проваливаемся дальше, к обычному "сесть/встать".
            if (hand == InteractionHand.MAIN_HAND && itemstack.isEmpty() && this.tryEatCarrot(player)) {
                return InteractionResult.sidedSuccess(this.level().isClientSide);
            }

            // Обычный ПКМ — приказать сесть/встать
            if (hand == InteractionHand.MAIN_HAND && !itemstack.is(ModItems.PIG_TREAT.get())) {
                if (!this.level().isClientSide) {
                    this.setOrderedToSit(!this.isOrderedToSit());
                    this.jumping = false;
                    this.navigation.stop();
                }
                return InteractionResult.sidedSuccess(this.level().isClientSide);
            }
        }

        return super.mobInteract(player, hand);
    }

    // --- СОХРАНЕНИЕ ДАННЫХ В NBT (Сохранение мира) ---

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putInt("PigLevel", this.getPigLevel());
        compound.putInt("PigExp", this.getPigExp());
        compound.putInt("SkillPoints", this.getSkillPoints());
        compound.put("Mutations", this.entityData.get(DATA_MUTATIONS).copy());
        compound.putInt("CarrotCooldown", this.carrotCooldown);
        if (this.courierChestPos != null) {
            compound.putLong("CourierChestPos", this.courierChestPos.asLong());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        if (compound.contains("PigLevel")) {
            this.setPigLevel(compound.getInt("PigLevel"));
        }
        if (compound.contains("PigExp")) {
            this.entityData.set(DATA_EXP, compound.getInt("PigExp"));
        }
        if (compound.contains("SkillPoints")) {
            this.setSkillPoints(compound.getInt("SkillPoints"));
        }
        if (compound.contains("Mutations")) {
            this.entityData.set(DATA_MUTATIONS, compound.getCompound("Mutations"));
            this.applyAllMutationEffects(); // пересчитываем бонусы мутаций после загрузки
        }
        if (compound.contains("CarrotCooldown")) {
            this.carrotCooldown = compound.getInt("CarrotCooldown");
        }
        if (compound.contains("CourierChestPos")) {
            this.courierChestPos = BlockPos.of(compound.getLong("CourierChestPos"));
        }
    }

    @Override
    public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob otherParent) {
        return null;
    }

    @Override
    public boolean doHurtTarget(Entity entity) {
        return super.doHurtTarget(entity);
    }

    @Override
    public boolean canAttackType(EntityType<?> type) {
        return type != this.getType();
    }
}