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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.level.Level;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import com.example.examplemod.ai.ArcherGoal;
import com.example.examplemod.ai.ResourceGatherGoal;
import com.example.examplemod.mutation.MutationDefinition;
import com.example.examplemod.mutation.Mutations;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.world.phys.Vec3;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.entity.projectile.ThrownPotion;

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
    // Изменение размера: 0 = обычный, 1 = маленький, 2 = большой
    private static final EntityDataAccessor<Integer> DATA_SIZE_STATE = SynchedEntityData.defineId(TameablePigEntity.class, EntityDataSerializers.INT);
    // Наездник: оседлана ли свинья Седлом (независимо от того, сидит ли на ней сейчас игрок)
    private static final EntityDataAccessor<Boolean> DATA_SADDLED = SynchedEntityData.defineId(TameablePigEntity.class, EntityDataSerializers.BOOLEAN);
    // Уничтожитель: ручной режим рейда — свинья постоянно в режиме бомбардировщика и бомбит без
    // оглядки на цель (управляется тумблером в GUI, для "летающей ТНТ-машины").
    private static final EntityDataAccessor<Boolean> DATA_RAID_MODE = SynchedEntityData.defineId(TameablePigEntity.class, EntityDataSerializers.BOOLEAN);
    // Пол: -1 = ещё не назначен (старое сохранение/новая сущность), 0 = мужской, 1 = женский.
    private static final EntityDataAccessor<Integer> DATA_GENDER = SynchedEntityData.defineId(TameablePigEntity.class, EntityDataSerializers.INT);
    // Команда: SIT / FOLLOW / ATTACK / WORK.
    private static final EntityDataAccessor<Integer> DATA_COMMAND = SynchedEntityData.defineId(TameablePigEntity.class, EntityDataSerializers.INT);
    // Голод хранится в условных единицах; максимальное значение зависит от уровня свиньи.
    private static final EntityDataAccessor<Integer> DATA_HUNGER = SynchedEntityData.defineId(TameablePigEntity.class, EntityDataSerializers.INT);

    /**
     * Открыты ли сейчас магические порталы Экстрасенса. Нужен клиенту: порталы — это часть
     * модели свиньи (см. PigAddonLayer), а решение "стреляем" принимается на сервере.
     * Синхронизируется всего дважды за залп (открылись / закрылись), а не каждый тик.
     */
    private static final EntityDataAccessor<Boolean> DATA_PSYCHIC_PORTAL = SynchedEntityData.defineId(TameablePigEntity.class, EntityDataSerializers.BOOLEAN);

    // Планшет управления: эти три параметра обязаны быть СИНХРОНИЗИРОВАНЫ с клиентом.
    // Раньше они были обычными серверными полями, из-за чего Планшет (клиентский экран)
    // всегда видел "сундук не привязан", "область не задана" и пустой белый список.
    private static final EntityDataAccessor<java.util.Optional<BlockPos>> DATA_CHEST_POS =
            SynchedEntityData.defineId(TameablePigEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final EntityDataAccessor<java.util.Optional<BlockPos>> DATA_AREA_P1 =
            SynchedEntityData.defineId(TameablePigEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final EntityDataAccessor<java.util.Optional<BlockPos>> DATA_AREA_P2 =
            SynchedEntityData.defineId(TameablePigEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    /** Белый список хранится как строка "id,id,id" — самый дешёвый способ синхронизировать набор. */
    private static final EntityDataAccessor<String> DATA_WHITELIST =
            SynchedEntityData.defineId(TameablePigEntity.class, EntityDataSerializers.STRING);

    // Модификаторы атрибутов для динамического увеличения ХП и Урона
    private static final ResourceLocation HEALTH_BONUS_ID = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "pig_health_bonus");
    private static final ResourceLocation DAMAGE_BONUS_ID = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "pig_damage_bonus");
    private static final ResourceLocation FLIGHT_SPEED_BONUS_ID = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "pig_flight_speed_bonus");
    private static final ResourceLocation DRAGON_SCALE_BONUS_ID = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "pig_dragon_scale_bonus");

    // Ангел: базовый кулдаун воскрешения и шаг его уменьшения за уровень (см. angelCooldownTicks()).
    private static final int ANGEL_COOLDOWN_BASE_TICKS = 6000; // 5 мин на 1-м уровне
    private static final int ANGEL_COOLDOWN_MIN_TICKS = 1200;  // 1 мин — потолок на максимальном уровне
    private static final int ANGEL_COOLDOWN_STEP_TICKS = 1200; // -1 мин за каждый уровень
    private static final int EXPLOSIVE_COOLDOWN_TICKS = 80;
    private static final int DRAGON_FIREBALL_INTERVAL = 50;
    private static final int PSYCHIC_INTERVAL = 70;

    // Взрывная свинья: урон взрыва растёт с уровнем (см. explosiveDamage()/explosiveRadius()).
    private static final float EXPLOSIVE_BASE_DAMAGE = 8.0F;
    private static final float EXPLOSIVE_DAMAGE_PER_LEVEL = 4.0F;
    private static final double EXPLOSIVE_BASE_RADIUS = 4.0D;
    private static final double EXPLOSIVE_RADIUS_PER_LEVEL = 0.4D;

    // Уничтожитель: фазы боя (лазеры+ракеты на земле) и режим бомбардировщика чередуются по таймеру.
    private static final int DESTROYER_GROUND_PHASE_TICKS = 300;  // ~15 сек боя "на земле" перед вылетом
    private static final int DESTROYER_BOMBER_PHASE_TICKS = 220;  // ~11 сек в режиме бомбардировщика

    private int angelCooldown = 0;
    private int explosiveCooldown = 0;
    private int dragonFireballCooldown = 0;
    private int psychicCooldown = 0;
    /** Сколько ещё тиков показывать порталы Экстрасенса (только сервер, клиенту уходит флаг). */
    private int psychicPortalTicks = 0;
    private int destroyerLaserCooldown = 0;
    private int destroyerRocketCooldown = 0;
    private int destroyerBombCooldown = 0;
    private int destroyerModePhaseTimer = DESTROYER_GROUND_PHASE_TICKS;
    private boolean bomberMode = false;
    private double bomberAltitudeTarget = 0.0D;
    private double bomberEntryY = 0.0D;

    public static final int MAX_LEVEL = 100;

    private final SimpleContainer inventory = new SimpleContainer(27);

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
                // Обязателен для FlyingMoveControl: без него Minecraft падает с
                // "Can't find attribute minecraft:generic.flying_speed".
                .add(Attributes.FLYING_SPEED, 0.3D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)      // Базовый урон: 4
                .add(Attributes.ATTACK_KNOCKBACK, 0.0D)   // нужен для мутации "Изменение размера" (отбрасывание у "Большого")
                .build();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_LEVEL, 1);
        builder.define(DATA_EXP, 0);
        builder.define(DATA_SKILL_POINTS, 0);
        builder.define(DATA_MUTATIONS, new CompoundTag());
        builder.define(DATA_SIZE_STATE, 0);
        builder.define(DATA_SADDLED, false);
        builder.define(DATA_RAID_MODE, false);
        builder.define(DATA_GENDER, -1);
        builder.define(DATA_COMMAND, PigCommand.FOLLOW.getId());
        builder.define(DATA_HUNGER, -1);
        builder.define(DATA_PSYCHIC_PORTAL, false);
        builder.define(DATA_CHEST_POS, java.util.Optional.empty());
        builder.define(DATA_AREA_P1, java.util.Optional.empty());
        builder.define(DATA_AREA_P2, java.util.Optional.empty());
        builder.define(DATA_WHITELIST, "");
    }

    // --- ЗВУКИ ---
    // Свинья должна звучать как свинья: хрюканье, боль, смерть и топот берём ванильные,
    // чтобы прирученная не отличалась на слух от обычной.

    @Override
    protected net.minecraft.sounds.SoundEvent getAmbientSound() {
        return net.minecraft.sounds.SoundEvents.PIG_AMBIENT;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource damageSource) {
        return net.minecraft.sounds.SoundEvents.PIG_HURT;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getDeathSound() {
        return net.minecraft.sounds.SoundEvents.PIG_DEATH;
    }

    @Override
    protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        this.playSound(net.minecraft.sounds.SoundEvents.PIG_STEP, 0.15F, 1.0F);
    }

    // --- ГЕТТЕРЫ И СЕТТЕРЫ ПАРАМЕТРОВ ---

    public int getPigLevel() {
        return this.entityData.get(DATA_LEVEL);
    }

    public void setPigLevel(int level) {
        // Не даём уровню превысить потолок, даже если кто-то передаст сюда число больше MAX_LEVEL
        // (например, при загрузке старого сохранения или командой).
        int oldMaxHunger = getMaxHunger();
        int clamped = Math.max(1, Math.min(level, MAX_LEVEL));
        this.entityData.set(DATA_LEVEL, clamped);
        this.updateStatsForLevel(); // Пересчитываем ХП и урон под новый уровень

        // При повышении уровня расширяем и запас голода. Если старый запас был полным,
        // новая вместимость также заполняется полностью; частично заполненный запас не "телепортируем" к максимуму.
        int rawHunger = this.entityData.get(DATA_HUNGER);
        int newMaxHunger = getMaxHunger();
        if (rawHunger < 0) {
            this.entityData.set(DATA_HUNGER, newMaxHunger);
        } else if (rawHunger >= oldMaxHunger) {
            this.entityData.set(DATA_HUNGER, newMaxHunger);
        } else {
            this.entityData.set(DATA_HUNGER, Math.min(rawHunger, newMaxHunger));
        }
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

    // =========================
    // Пол, команда и голод
    // =========================

    public boolean isGenderAssigned() {
        return this.entityData.get(DATA_GENDER) >= 0;
    }

    /** 0 = мужской, 1 = женский. Для UI/модели удобнее получать читаемое значение отдельно. */
    public boolean isFemale() {
        return this.entityData.get(DATA_GENDER) == 1;
    }

    public void setFemale(boolean female) {
        this.entityData.set(DATA_GENDER, female ? 1 : 0);
    }

    public void randomizeGenderIfNeeded() {
        if (!isGenderAssigned()) {
            setFemale(this.getRandom().nextBoolean());
        }
    }

    public PigCommand getCommand() {
        return PigCommand.fromId(this.entityData.get(DATA_COMMAND));
    }

    public void setCommand(PigCommand command) {
        if (command == null) {
            command = PigCommand.FOLLOW;
        }
        this.entityData.set(DATA_COMMAND, command.getId());
        // Старые AI-цели Minecraft завязаны на isOrderedToSit(), поэтому команда "Сидеть"
        // остаётся полностью совместима с уже существующей логикой SitWhenOrderedToGoal.
        super.setOrderedToSit(command == PigCommand.SIT);
        if (command != PigCommand.FOLLOW) {
            this.navigation.stop();
        }
        if (command != PigCommand.ATTACK) {
            this.setTarget(null);
        }
    }

    public void cycleCommand() {
        if (this.level().isClientSide) {
            return;
        }
        setCommand(getCommand().next());
        if (this.getOwner() instanceof Player player) {
            player.sendSystemMessage(Component.translatable("message.pig_mod.command_changed", Component.translatable(getCommand().getTranslationKey())));
        }
    }

    /** Нужно ли свинье следовать за хозяином в текущем режиме.
     * В режиме "Атаковать монстров" свинья тоже держится рядом с хозяином,
     * но при появлении враждебной цели переключается на её атаку.
     * В режиме "Работать" следование отключено: без рабочих мутаций свинья
     * просто остаётся на месте до реализации системы территорий. */
    public boolean shouldFollowOwner() {
        return getCommand() == PigCommand.FOLLOW
                || getCommand() == PigCommand.ATTACK;
    }

    public boolean isWorkCommand() {
        return getCommand() == PigCommand.WORK;
    }

    public boolean isAttackCommand() {
        return getCommand() == PigCommand.ATTACK;
    }

    public boolean hasWorkMutation() {
        return hasMutation("lumberjack") || hasMutation("miner")
                || hasMutation("farmer") || hasMutation("fisher");
    }

    public int getMaxHunger() {
        // Lvl 1 = 100, Lvl 100 = 595. Поэтому прокачка заметно увеличивает автономность.
        return 100 + Math.max(0, getPigLevel() - 1) * 5;
    }

    public int getHunger() {
        int raw = this.entityData.get(DATA_HUNGER);
        return raw < 0 ? getMaxHunger() : Math.min(raw, getMaxHunger());
    }

    public void setHunger(int hunger) {
        this.entityData.set(DATA_HUNGER, Math.max(0, Math.min(getMaxHunger(), hunger)));
    }

    public void restoreHunger(int amount) {
        if (amount > 0) {
            setHunger(getHunger() + amount);
        }
    }

    private void tickHunger() {
        if (this.entityData.get(DATA_HUNGER) < 0) {
            this.entityData.set(DATA_HUNGER, getMaxHunger());
            return;
        }
        // 1 единица примерно раз в минуту. При стартовом запасе 100 это более 1.5 часа
        // до полного опустошения; высокоуровневые свиньи живут без кормёжки ещё дольше.
        final int HUNGER_DRAIN_INTERVAL = 1200;
        if (this.tickCount % HUNGER_DRAIN_INTERVAL == 0 && getHunger() > 0) {
            setHunger(getHunger() - 1);
        }
    }

    private boolean hasCompatibleMateNearby() {
        if (this.isBaby() || !this.isTame()) {
            return false;
        }
        return !this.level().getEntitiesOfClass(TameablePigEntity.class, this.getBoundingBox().inflate(8.0D),
                other -> other != this && other.isAlive() && !other.isBaby()
                        && other.isTame() && this.isFemale() != other.isFemale()).isEmpty();
    }

    // --- ОБЩИЙ ХЕЛПЕР ДЛЯ МУТАЦИЙ-АТРИБУТОВ (здоровье/броня/урон/скорость и т.п.) ---
    // Используется из эффектов в Mutations.java — каждая такая мутация просто ставит/убирает
    // свой именованный модификатор на нужном атрибуте, не трогая базовые значения и другие бонусы
    // (например, бонус от уровня самой свиньи, который считается отдельно в updateStatsForLevel).

    /** Реальные характеристики для GUI: только базовые значения + мутационные бонусы. */
    public double getDisplayedMaxHealth() {
        double value = 20.0D;
        value += this.getMutationLevel("health_boost") * 4.0D;
        if (this.getSizeState() == 2) {
            value += this.getMutationLevel("resize") * 3.0D;
        }
        return value;
    }

    public double getDisplayedAttackDamage() {
        double value = 4.0D;
        value += this.getMutationLevel("damage");
        if (this.getSizeState() == 2) {
            value += this.getMutationLevel("resize") * 0.6D;
        }
        return value;
    }

    public double getDisplayedArmor() {
        return this.getMutationLevel("armor");
    }

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
        if (attribute == Attributes.MAX_HEALTH && this.getHealth() > this.getMaxHealth()) {
            this.setHealth(this.getMaxHealth());
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
        // Состояние мутации могло измениться в GUI между тиками. Переключаем контроллеры
        // до запуска GoalSelector, чтобы FollowOwner/RandomFlying сразу получили нужную навигацию.
        this.updateFlightMovementMode();
        super.customServerAiStep();

        if (this.carrotCooldown > 0) {
            this.carrotCooldown--;
        }

        randomizeGenderIfNeeded();
        tickHunger();

        // --- Регенерация ---
        // Если свинья действительно голодна, пассивная регенерация не работает — это единственный
        // мягкий игровой эффект голода. Умирать от голода она не будет.
        if (this.hasMutation("regeneration") && this.getHunger() > 0 && this.getHealth() < this.getMaxHealth()) {
            int level = this.getMutationLevel("regeneration");
            int interval = regenIntervalTicks(level);
            if (interval > 0 && this.tickCount % interval == 0) {
                this.heal(1.0F);
            }
        }

        tickTier2Mutations();
        tickTier3Mutations();
        tickTier4Mutations();
        tickEnderPigEmergencyEscape();
        tickCourier();
        tickFisher();
        tickAlchemist();

        if (this.isOrderedToSit()) {
            this.navigation.stop();
            this.setDeltaMovement(Vec3.ZERO);
            this.setNoGravity(this.wantsNoGravity());
            this.fallDistance = 0.0F;
        }
    }

    /** Интервал (в тиках) между тиками пассивной регенерации по уровню мутации.
     * Lvl1 ≈ 4.5с, Lvl10 = 0.5с (1 HP за тик регенерации).
     */
    public static int regenIntervalTicks(int level) {
        return Math.max(10, 100 - level * 10);
    }

    // --- TIER 2: РАБОЧИЕ ПРОФЕССИИ И КУРЬЕР ---

    /**
     * Внутренний буфер для добычи "рабочих" мутаций (Лесоруб/Фермер/Шахтёр/Рыбак) — переиспользуем
     * внутренний инвентарь свиньи (27 слотов). Курьер разгружает этот буфер в привязанный сундук; без Курьера,
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

    // --- Курьер / Планшет: привязанный сундук, рабочая область и белый список ---
    // Всё это синхронизировано через entityData (см. DATA_CHEST_POS / DATA_AREA_P1 /
    // DATA_AREA_P2 / DATA_WHITELIST), поэтому клиентский Планшет видит реальные значения.

    private java.util.Set<ResourceLocation> whitelistCache = new java.util.LinkedHashSet<>();
    private String whitelistCacheRaw = null;

    private static java.util.Optional<BlockPos> optional(BlockPos pos) {
        return java.util.Optional.ofNullable(pos);
    }

    // ---- Рабочая область ----

    public void setWorkAreaPoint1(BlockPos pos){ this.entityData.set(DATA_AREA_P1, optional(pos)); }
    public void setWorkAreaPoint2(BlockPos pos){ this.entityData.set(DATA_AREA_P2, optional(pos)); }
    public BlockPos getWorkAreaPoint1(){ return this.entityData.get(DATA_AREA_P1).orElse(null); }
    public BlockPos getWorkAreaPoint2(){ return this.entityData.get(DATA_AREA_P2).orElse(null); }
    public boolean hasWorkArea(){ return getWorkAreaPoint1()!=null && getWorkAreaPoint2()!=null; }

    /** Сброс области (кнопка в Планшете). Свинья снова работает где угодно. */
    public void clearWorkArea(){
        this.entityData.set(DATA_AREA_P1, java.util.Optional.empty());
        this.entityData.set(DATA_AREA_P2, java.util.Optional.empty());
    }

    public boolean isInsideWorkArea(BlockPos pos){
        BlockPos a = getWorkAreaPoint1();
        BlockPos b = getWorkAreaPoint2();
        if(a==null || b==null) return true;
        int minX=Math.min(a.getX(),b.getX()), maxX=Math.max(a.getX(),b.getX());
        int minZ=Math.min(a.getZ(),b.getZ()), maxZ=Math.max(a.getZ(),b.getZ());
        return pos.getX()>=minX && pos.getX()<=maxX && pos.getZ()>=minZ && pos.getZ()<=maxZ;
    }

    /** Центр области — точка, к которой свинья возвращается, если её вынесло за границу. */
    public BlockPos getWorkAreaCenter(){
        BlockPos a = getWorkAreaPoint1();
        BlockPos b = getWorkAreaPoint2();
        if(a==null || b==null) return null;
        return new BlockPos((a.getX()+b.getX())/2, (a.getY()+b.getY())/2, (a.getZ()+b.getZ())/2);
    }

    /** Ближайшая точка внутри области (по XZ) — чтобы свинья шла к границе, а не в центр. */
    public BlockPos clampToWorkArea(BlockPos pos){
        BlockPos a = getWorkAreaPoint1();
        BlockPos b = getWorkAreaPoint2();
        if(a==null || b==null) return pos;
        int minX=Math.min(a.getX(),b.getX()), maxX=Math.max(a.getX(),b.getX());
        int minZ=Math.min(a.getZ(),b.getZ()), maxZ=Math.max(a.getZ(),b.getZ());
        int x = Math.max(minX+1, Math.min(maxX-1, pos.getX()));
        int z = Math.max(minZ+1, Math.min(maxZ-1, pos.getZ()));
        if (maxX - minX < 2) x = (minX + maxX) / 2;
        if (maxZ - minZ < 2) z = (minZ + maxZ) / 2;
        return new BlockPos(x, pos.getY(), z);
    }

    // ---- Белый список ----

    private java.util.Set<ResourceLocation> whitelistIds(){
        String raw = this.entityData.get(DATA_WHITELIST);
        if(!raw.equals(whitelistCacheRaw)){
            java.util.Set<ResourceLocation> parsed = new java.util.LinkedHashSet<>();
            if(!raw.isEmpty()){
                for(String part : raw.split(",")){
                    if(part.isEmpty()) continue;
                    try { parsed.add(ResourceLocation.parse(part)); } catch(Exception ignored){}
                }
            }
            this.whitelistCache = parsed;
            this.whitelistCacheRaw = raw;
        }
        return this.whitelistCache;
    }

    private void writeWhitelist(java.util.Collection<ResourceLocation> ids){
        StringBuilder sb = new StringBuilder();
        for(ResourceLocation id : ids){
            if(sb.length()>0) sb.append(',');
            sb.append(id.toString());
        }
        this.entityData.set(DATA_WHITELIST, sb.toString());
    }

    /** Копия белого списка — нужна Планшету, чтобы показать реальные записи. */
    public java.util.List<ResourceLocation> getWhitelistIds(){
        return new java.util.ArrayList<>(whitelistIds());
    }

    public void toggleWhitelistEntityType(net.minecraft.world.entity.EntityType<?> type){
        ResourceLocation id=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if(id==null) return;
        java.util.Set<ResourceLocation> set = new java.util.LinkedHashSet<>(whitelistIds());
        if(!set.add(id)) set.remove(id);
        writeWhitelist(set);
    }

    public void clearWhitelist(){
        writeWhitelist(java.util.List.of());
    }

    public boolean isWhitelisted(net.minecraft.world.entity.EntityType<?> type){
        ResourceLocation id=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return id!=null && whitelistIds().contains(id);
    }

    public int getWhitelistCount(){ return whitelistIds().size(); }

    // ---- Привязанный сундук ----

    public void setCourierChestPos(BlockPos pos) {
        this.entityData.set(DATA_CHEST_POS, optional(pos));
    }

    public BlockPos getCourierChestPos() {
        return this.entityData.get(DATA_CHEST_POS).orElse(null);
    }

    /** Сброс привязки к сундуку (кнопка в Планшете). */
    public void clearCourierChest() {
        this.entityData.set(DATA_CHEST_POS, java.util.Optional.empty());
    }

    private void tickCourier() {
        BlockPos chestPos = this.getCourierChestPos();
        if (!this.hasMutation("courier") || chestPos == null) {
            return;
        }
        if (this.tickCount % 100 != 0) { // раз в 5 сек — не нужно чаще, это не хопер
            return;
        }
        if (!(this.level() instanceof ServerLevel serverLevel) || !serverLevel.isLoaded(chestPos)) {
            return;
        }
        if (!(serverLevel.getBlockEntity(chestPos) instanceof Container chest)) {
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
    // --- TIER 4: легендарные мутации ---

    /** Кулдаун воскрешения Ангела на данном уровне мутации — чем выше lvl, тем короче (см. константы выше). */
    public static int angelCooldownTicks(int level) {
        int lvl = Math.max(1, level);
        return Math.max(ANGEL_COOLDOWN_MIN_TICKS, ANGEL_COOLDOWN_BASE_TICKS - (lvl - 1) * ANGEL_COOLDOWN_STEP_TICKS);
    }

    /** Урон взрыва Взрывной свиньи на данном уровне мутации. */
    public static float explosiveDamage(int level) {
        int lvl = Math.max(1, level);
        return EXPLOSIVE_BASE_DAMAGE + (lvl - 1) * EXPLOSIVE_DAMAGE_PER_LEVEL;
    }

    /** Радиус взрыва Взрывной свиньи на данном уровне мутации. */
    public static double explosiveRadius(int level) {
        int lvl = Math.max(1, level);
        return EXPLOSIVE_BASE_RADIUS + (lvl - 1) * EXPLOSIVE_RADIUS_PER_LEVEL;
    }

    /** Урон огненного снаряда Дракона на данном уровне мутации. */
    public static float dragonFireballDamage(int level) {
        int lvl = Math.max(1, level);
        return 5.0F + (lvl - 1) * 1.5F;
    }

    /** Время горения цели от снаряда Дракона (в секундах) на данном уровне мутации. */
    public static int dragonFireballFireSeconds(int level) {
        int lvl = Math.max(1, level);
        return 4 + (lvl - 1);
    }

    /** С какого уровня Дракон при попадании выпускает облако дыхания, как Эндер-дракон. */
    public static boolean dragonHasBreathCloud(int level) {
        return level >= 4;
    }

    /** Скорость лазерного снаряда Экстрасенса на данном уровне мутации — летит по прямой, без торможения
     *  и без гравитации (см. PigLaser), поэтому это прямое значение "скорости пули", а не множитель. */
    public static double psychicProjectileSpeed(int level) {
        int lvl = Math.max(1, level);
        return 2.5D + (lvl - 1) * 0.7D;
    }

    /** Полный урон снаряда Экстрасенса при попадании (прямое + область поражения считается от этого числа). */
    public static float psychicDamage(int level) {
        int lvl = Math.max(1, level);
        return 6.0F + (lvl - 1) * 2.5F;
    }

    /** Длительность иссушения от снаряда Экстрасенса (в тиках) на данном уровне мутации. */
    public static int psychicWitherDurationTicks(int level) {
        int lvl = Math.max(1, level);
        return 100 + (lvl - 1) * 20;
    }

    /** Сила иссушения (амплификатор эффекта) от снаряда Экстрасенса — усиливается на верхних уровнях. */
    public static int psychicWitherAmplifier(int level) {
        return level >= 4 ? 1 : 0;
    }

    /** Сколько порталов одновременно открывает Экстрасенс на данном уровне мутации. */
    public static int psychicPortalCount(int level) {
        int lvl = Math.max(1, level);
        return 1 + (lvl - 1) / 2;
    }

    // --- Уничтожитель: масштабирование по уровню ---

    /** Урон одного глазного лазера Уничтожителя на данном уровне мутации ("спам"-атака, часто и понемногу). */
    public static float destroyerLaserDamage(int level) {
        int lvl = Math.max(1, level);
        return 1.0F + (lvl - 1) * 0.4F;
    }

    /** Интервал между выстрелами лазера (в тиках) — чем выше lvl, тем плотнее "спам". */
    public static int destroyerLaserIntervalTicks(int level) {
        int lvl = Math.max(1, level);
        return Math.max(3, 10 - (lvl - 1));
    }

    /** Урон ракеты Уничтожителя при взрыве на данном уровне мутации. */
    public static float destroyerRocketDamage(int level) {
        int lvl = Math.max(1, level);
        return 10.0F + (lvl - 1) * 3.0F;
    }

    /** Радиус взрыва ракеты (ракеты, в отличие от других способностей, разрушают блоки). */
    public static float destroyerRocketRadius(int level) {
        int lvl = Math.max(1, level);
        return 2.5F + (lvl - 1) * 0.4F;
    }

    /** Интервал между запусками ракет (в тиках) на данном уровне мутации. */
    public static int destroyerRocketIntervalTicks(int level) {
        int lvl = Math.max(1, level);
        return Math.max(60, 140 - (lvl - 1) * 20);
    }

    /** Урон одной бомбы в режиме бомбардировщика на данном уровне мутации. */
    public static float destroyerBombDamage(int level) {
        int lvl = Math.max(1, level);
        return 14.0F + (lvl - 1) * 4.0F;
    }

    /** Радиус взрыва бомбы (бомбы тоже разрушают блоки и поджигают). */
    public static float destroyerBombRadius(int level) {
        int lvl = Math.max(1, level);
        return 3.0F + (lvl - 1) * 0.5F;
    }

    /** Интервал между сбросом бомб (в тиках) — от ~4 сек на 1 lvl до ~2 сек на макс. уровне, как и просили. */
    public static int destroyerBombIntervalTicks(int level) {
        int lvl = Math.max(1, level);
        return Math.max(40, 80 - (lvl - 1) * 10);
    }

    /** Высота полёта над поверхностью в режиме бомбардировщика (20 блоков на 1 lvl, до 30 на максимальном). */
    public static double destroyerBomberAltitude(int level) {
        int lvl = Math.max(1, level);
        return 20.0D + Math.min(1.0D, (lvl - 1) / 4.0D) * 10.0D;
    }

    public void refreshDragonState() {
        this.setAttributeBonus(Attributes.SCALE, DRAGON_SCALE_BONUS_ID,
                // Раньше было +1.50 (свинья становилась огромной). Теперь размер как у обычной свиньи,
                // но чуть крупнее — небольшая прибавка к масштабу.
                this.hasMutation("dragon") ? 0.20D : 0.0D,
                AttributeModifier.Operation.ADD_VALUE);
    }

    private void tickTier4Mutations() {
        if (angelCooldown > 0) angelCooldown--;
        if (explosiveCooldown > 0) explosiveCooldown--;
        if (dragonFireballCooldown > 0) dragonFireballCooldown--;
        if (psychicCooldown > 0) psychicCooldown--;
        if (psychicPortalTicks > 0 && --psychicPortalTicks == 0) {
            this.entityData.set(DATA_PSYCHIC_PORTAL, false);
        }

        if (this.hasMutation("ethereal")) {
            if (this.hasMutation("flight")) {
                // Эфир + Полёт: полноценный NoClip — свободный полёт сквозь любые блоки.
                this.noPhysics = true;
                this.setNoGravity(true);
                this.fallDistance = 0.0F;
            } else {
                // Только Эфир: свинья остаётся на поверхности (гравитация и обычная коллизия с землёй
                // работают как обычно), но проходит сквозь блок, если он оказался прямо у неё на пути.
                // Раньше noPhysics включался безусловно, из-за чего свинья проваливалась сквозь пол
                // и вылетала из мира — теперь коллизия отключается точечно, только когда впереди преграда.
                this.setNoGravity(false);
                this.noPhysics = isEtherealObstacleAhead();
            }
        } else if (this.noPhysics) {
            this.noPhysics = false;
        }

        if (this.hasMutation("dragon")) {
            this.refreshDragonState();
            tickDragonFireball();
        }
        if (this.hasMutation("explosive_pig")) {
            tickExplosivePig();
        }
        if (this.hasMutation("psychic")) {
            tickPsychic();
        }
        if (this.hasMutation("destroyer")) {
            tickDestroyer();
        } else if (this.bomberMode || this.isRaidMode()) {
            // Мутацию сняли прямо во время полёта бомбардировщика (или с активным рейд-режимом) —
            // аккуратно возвращаем свинью на землю и сбрасываем ручной тумблер.
            this.setRaidMode(false);
            exitBomberMode();
        }
    }

    /** Учитывает и "Полёт", и активный режим бомбардировщика Уничтожителя — иначе travel()/телепорт
     *  каждый тик сбрасывали бы невесомость бомбардировщика обратно, ориентируясь только на "Полёт". */
    private boolean wantsNoGravity() {
        return this.hasMutation("flight") || this.bomberMode;
    }

    /**
     * Проверяет, есть ли прямо по курсу движения (или по направлению взгляда, если свинья почти
     * не движется) твёрдый блок — тогда мутация "Эфир" на секунду отключает коллизию, чтобы свинья
     * прошла сквозь него, а не застряла или не начала проваливаться вниз.
     */
    private boolean isEtherealObstacleAhead() {
        Vec3 motion = this.getDeltaMovement();
        double horizontalLengthSqr = motion.x * motion.x + motion.z * motion.z;
        Vec3 dir;
        if (horizontalLengthSqr > 1.0E-4D) {
            dir = new Vec3(motion.x, 0.0D, motion.z).normalize();
        } else {
            float yawRad = this.getYRot() * ((float) Math.PI / 180F);
            dir = new Vec3(-Math.sin(yawRad), 0.0D, Math.cos(yawRad));
        }

        double probe = Math.max(0.6D, this.getBbWidth() * 0.75D + 0.3D);
        BlockPos feet = BlockPos.containing(this.getX() + dir.x * probe, this.getY() + 0.1D, this.getZ() + dir.z * probe);
        BlockPos head = feet.above(Math.max(0, (int) Math.ceil(this.getBbHeight()) - 1));

        return isEtherealBlocking(feet) || isEtherealBlocking(head);
    }

    private boolean isEtherealBlocking(BlockPos pos) {
        net.minecraft.world.level.block.state.BlockState state = this.level().getBlockState(pos);
        return !state.isAir() && state.blocksMotion();
    }

    private void tickExplosivePig() {
        if (explosiveCooldown > 0) return;
        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) return;

        double hitRadius = Math.max(2.4D, this.getBbWidth() + 1.2D);
        if (this.distanceToSqr(target) > hitRadius * hitRadius) return;

        explosiveCooldown = EXPLOSIVE_COOLDOWN_TICKS;
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.EXPLOSION, this.getX(), this.getY(0.6D), this.getZ(),
                    2, 0.2D, 0.2D, 0.2D, 0.0D);
            serverLevel.playSound(null, this.blockPosition(), net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(),
                    net.minecraft.sounds.SoundSource.HOSTILE, 1.2F, 0.9F);
        }

        int level = Math.max(1, this.getMutationLevel("explosive_pig"));
        double radius = explosiveRadius(level);
        float damage = explosiveDamage(level);
        for (LivingEntity entity : this.level().getEntitiesOfClass(LivingEntity.class,
                this.getBoundingBox().inflate(radius), e -> e.isAlive() && e != this)) {
            if (this.isAlliedTo(entity) || entity == this.getOwner()) continue;

            double distance = Math.sqrt(this.distanceToSqr(entity));
            float scaledDamage = (float) Math.max(2.0D, damage * (1.0D - Math.min(1.0D, distance / radius)));
            entity.hurt(this.level().damageSources().mobAttack(this), scaledDamage);

            Vec3 push = entity.position().subtract(this.position());
            if (push.lengthSqr() > 0.0001D) {
                Vec3 knockback = push.normalize().scale(0.65D * (1.0D - Math.min(0.75D, distance / radius)));
                entity.push(knockback.x, 0.35D, knockback.z);
            }
        }
    }

    private void tickDragonFireball() {
        if (dragonFireballCooldown > 0) return;
        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive() || this.distanceToSqr(target) > 24.0D * 24.0D) return;

        Vec3 origin = this.position().add(0.0D, this.getBbHeight() * 0.65D, 0.0D);
        Vec3 direction = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D).subtract(origin);
        if (direction.lengthSqr() < 0.01D) return;

        int level = Math.max(1, this.getMutationLevel("dragon"));
        PigDragonFireball fireball = new PigDragonFireball(this.level(), this, direction.normalize(),
                dragonFireballDamage(level), dragonFireballFireSeconds(level), dragonHasBreathCloud(level));
        fireball.setPos(origin);
        this.level().addFreshEntity(fireball);
        dragonFireballCooldown = DRAGON_FIREBALL_INTERVAL;

        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.FLAME, origin.x, origin.y, origin.z,
                    10, 0.12D, 0.12D, 0.12D, 0.02D);
        }
    }

    private void tickPsychic() {
        if (psychicCooldown > 0) return;
        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive() || this.distanceToSqr(target) > 30.0D * 30.0D) return;

        Vec3 origin = this.position().add(0.0D, this.getBbHeight() * 0.7D, 0.0D);
        Vec3 toTarget = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D).subtract(origin);
        if (toTarget.lengthSqr() < 0.01D) return;
        Vec3 forward = toTarget.normalize();

        // Боковой вектор — порталы должны появляться сбоку от свиньи, а не перед ней.
        Vec3 side = new Vec3(-forward.z, 0.0D, forward.x);
        if (side.lengthSqr() < 0.01D) {
            // Цель прямо над/под свиньёй — горизонтальной проекции направления нет,
            // берём боковой вектор от текущего поворота свиньи.
            float yawRad = this.getYRot() * ((float) Math.PI / 180F);
            side = new Vec3(Math.cos(yawRad), 0.0D, Math.sin(yawRad));
        } else {
            side = side.normalize();
        }

        int level = Math.max(1, this.getMutationLevel("psychic"));
        int portals = psychicPortalCount(level);
        double speed = psychicProjectileSpeed(level);
        float damage = psychicDamage(level);
        int witherDuration = psychicWitherDurationTicks(level);
        int witherAmplifier = psychicWitherAmplifier(level);

        for (int i = 0; i < portals; i++) {
            // Порталы чередуются по обе стороны от свиньи, каждая следующая пара — чуть дальше вбок.
            int pairIndex = i / 2;
            double sign = (i % 2 == 0) ? 1.0D : -1.0D;
            Vec3 portalPos = origin.add(side.scale(sign * (1.1D + pairIndex * 0.5D)));

            if (this.level() instanceof ServerLevel serverLevel) {
                // Белых частиц END_ROD больше нет — вместо них у свиньи по бокам открывается
                // настоящий 2D-портал (часть модели, см. PigAddonLayer). Немного фиолетовых
                // частиц оставлено как след вокруг портала.
                serverLevel.sendParticles(ParticleTypes.PORTAL, portalPos.x, portalPos.y, portalPos.z,
                        12, 0.25D, 0.35D, 0.25D, 0.08D);
            }

            Vec3 shotDir = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D).subtract(portalPos);
            if (shotDir.lengthSqr() < 0.01D) continue;
            Vec3 aimed = shotDir.normalize().scale(speed);

            // Из портала вылетает снаряд-лазер, а не огненный шар.
            PigLaser laser = new PigLaser(this.level(), this, aimed, damage, witherDuration, witherAmplifier);
            laser.setPos(portalPos);
            this.level().addFreshEntity(laser);
        }

        // Открываем порталы на модели — они держатся чуть дольше самого залпа.
        this.psychicPortalTicks = 30;
        this.entityData.set(DATA_PSYCHIC_PORTAL, true);

        psychicCooldown = PSYCHIC_INTERVAL;
    }

    /**
     * Уничтожитель: киборг-режим. На земле — глазные лазеры (частая слабая "спам"-атака, блоки не трогает)
     * и ракеты (редкие, мощные, ВЗРЫВАЮТ БЛОКИ — единственная способность в моде, которой это разрешено).
     * Раз в некоторое время трансформируется в бомбардировщика: взлетает на 20-30 блоков над поверхностью,
     * зависает и сбрасывает вниз тяжёлые бомбы, которые тоже ломают блоки и поджигают landing-зону.
     * Игрок(и) рядом никогда не получают урон/отдачу ни от ракет, ни от бомб (см. PigRocket/PigBomb).
     */
    private void tickDestroyer() {
        if (destroyerLaserCooldown > 0) destroyerLaserCooldown--;
        if (destroyerRocketCooldown > 0) destroyerRocketCooldown--;
        if (destroyerBombCooldown > 0) destroyerBombCooldown--;
        if (destroyerModePhaseTimer > 0) destroyerModePhaseTimer--;

        int level = Math.max(1, this.getMutationLevel("destroyer"));
        LivingEntity target = this.getTarget();
        boolean hasTarget = target != null && target.isAlive();

        if (this.isRaidMode()) {
            // Ручной режим рейда: свинья постоянно держится бомбардировщиком и бомбит по таймеру
            // независимо от того, есть ли цель снизу — управляемая "летающая ТНТ-машина".
            if (!this.bomberMode) {
                enterBomberMode(level);
            }
            tickBomberMode(level, hasTarget ? target : null);
            return;
        }

        if (this.bomberMode) {
            tickBomberMode(level, hasTarget ? target : null);
            if (destroyerModePhaseTimer <= 0 || !hasTarget) {
                exitBomberMode();
            }
        } else {
            if (hasTarget) {
                tickDestroyerLasers(level, target);
                tickDestroyerRockets(level, target);
            }
            if (destroyerModePhaseTimer <= 0 && hasTarget) {
                enterBomberMode(level);
            }
        }
    }

    /**
     * Глазные лазеры Уничтожителя. Раньше это была линия из частиц редстоуна плюс мгновенный
     * урон по цели — теперь свинья реально спамит снарядами-лазерами из двух "стволов" по бокам
     * морды. Снаряд сам считает попадание, сам гаснет о стену и не трогает хозяина, поэтому
     * отдельная проверка стены и ручной target.hurt() больше не нужны.
     */
    private void tickDestroyerLasers(int level, LivingEntity target) {
        if (destroyerLaserCooldown > 0) return;
        double range = 20.0D;
        if (this.distanceToSqr(target) > range * range) return;

        Vec3 eyePos = this.getEyePosition();
        Vec3 targetPos = target.getEyePosition().subtract(0.0D, target.getBbHeight() * 0.1D, 0.0D);
        Vec3 toTarget = targetPos.subtract(eyePos);
        if (toTarget.lengthSqr() < 0.01D) return;

        Vec3 forward = toTarget.normalize();
        Vec3 side = new Vec3(-forward.z, 0.0D, forward.x);
        side = side.lengthSqr() < 0.01D ? new Vec3(1.0D, 0.0D, 0.0D) : side.normalize();

        float damage = destroyerLaserDamage(level);
        for (double sign : new double[]{1.0D, -1.0D}) {
            Vec3 origin = eyePos.add(side.scale(sign * 0.22D)).add(forward.scale(0.4D));
            Vec3 aim = targetPos.subtract(origin);
            if (aim.lengthSqr() < 0.01D) continue;

            PigLaser laser = new PigLaser(this.level(), this, aim.normalize().scale(1.8D), damage);
            laser.setPos(origin);
            this.level().addFreshEntity(laser);
        }

        this.playSound(net.minecraft.sounds.SoundEvents.ARROW_SHOOT, 0.4F, 1.9F);
        destroyerLaserCooldown = destroyerLaserIntervalTicks(level);
    }

    private void tickDestroyerRockets(int level, LivingEntity target) {
        if (destroyerRocketCooldown > 0) return;
        double range = 20.0D;
        if (this.distanceToSqr(target) > range * range) return;

        Vec3 origin = this.position().add(0.0D, this.getBbHeight() * 0.6D, 0.0D);
        Vec3 direction = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D).subtract(origin);
        if (direction.lengthSqr() < 0.01D) return;

        PigRocket rocket = new PigRocket(this.level(), this, direction.normalize().scale(1.6D),
                destroyerRocketDamage(level), destroyerRocketRadius(level));
        rocket.setPos(origin);
        this.level().addFreshEntity(rocket);
        destroyerRocketCooldown = destroyerRocketIntervalTicks(level);

        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SMOKE, origin.x, origin.y, origin.z, 8, 0.1D, 0.1D, 0.1D, 0.02D);
        }
    }

    private void enterBomberMode(int level) {
        this.bomberMode = true;
        this.destroyerModePhaseTimer = DESTROYER_BOMBER_PHASE_TICKS;
        this.destroyerBombCooldown = 0;
        this.bomberEntryY = this.getY();
        this.bomberAltitudeTarget = findSurfaceY(this.blockPosition()) + destroyerBomberAltitude(level);
        this.setNoGravity(true);
        this.fallDistance = 0.0F;
    }

    private void exitBomberMode() {
        this.bomberMode = false;
        this.destroyerModePhaseTimer = DESTROYER_GROUND_PHASE_TICKS;
        this.setNoGravity(this.wantsNoGravity());
    }

    private void tickBomberMode(int level, LivingEntity target) {
        // Если сверху сидит игрок — движением рулит он сам (через штатное управление Полётом
        // в travel()), здесь только держим невесомость и сбрасываем бомбы по таймеру.
        boolean playerControlled = this.isVehicle() && this.getControllingPassenger() instanceof Player;

        if (!playerControlled) {
            Vec3 pos = this.position();
            double diff = this.bomberAltitudeTarget - pos.y;
            double verticalSpeed = net.minecraft.util.Mth.clamp(diff * 0.08D, -0.6D, 0.6D);

            Vec3 horizontal = Vec3.ZERO;
            if (target != null) {
                Vec3 toTarget = new Vec3(target.getX() - pos.x, 0.0D, target.getZ() - pos.z);
                if (toTarget.lengthSqr() > 4.0D) {
                    horizontal = toTarget.normalize().scale(0.15D);
                }
            }

            this.setDeltaMovement(horizontal.x, verticalSpeed, horizontal.z);
            this.fallDistance = 0.0F;
        }

        // Не бомбим, пока свинья ещё толком не взлетела — иначе первая бомба падает и взрывается
        // прямо под ней у земли. "Готова бомбить" — либо реально набрала высоту с момента входа
        // в режим, либо (если игрок сам поднял её повыше вручную) уже близко к целевой высоте.
        boolean readyToBomb = (this.getY() - this.bomberEntryY) >= 6.0D
                || this.getY() >= this.bomberAltitudeTarget - 4.0D;

        if (destroyerBombCooldown <= 0 && readyToBomb) {
            dropBomb(level);
            destroyerBombCooldown = destroyerBombIntervalTicks(level);
        }
    }

    private void dropBomb(int level) {
        PigBomb bomb = new PigBomb(this.level(), this, destroyerBombDamage(level), destroyerBombRadius(level));
        bomb.setPos(this.getX(), this.getY() - 0.5D, this.getZ());
        bomb.setDeltaMovement(0.0D, -0.1D, 0.0D);
        this.level().addFreshEntity(bomb);

        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.CLOUD, this.getX(), this.getY() - 0.5D, this.getZ(),
                    6, 0.2D, 0.05D, 0.2D, 0.01D);
        }
    }

    /** Ищет высоту поверхности (первый непустой блок сверху вниз) под указанной колонкой — точка,
     *  от которой отсчитывается высота полёта бомбардировщика. */
    private double findSurfaceY(BlockPos start) {
        BlockPos.MutableBlockPos pos = start.mutable();
        int minY = this.level().getMinBuildHeight();
        while (pos.getY() > minY && this.level().getBlockState(pos).isAir()) {
            pos.move(0, -1, 0);
        }
        return pos.getY() + 1.0D;
    }

    // --- TIER 3: иммунитеты, баффы, размер ---

    /**
     * Изменение размера. Переключается кнопкой на Вкладке 1 GUI (не автоматически) — см. PigScreen
     * и CyclePigSizePacket. Использует ванильный атрибут generic.scale (влияет и на визуальный
     * размер, и на хитбокс одновременно — отдельно ничего лепить не пришлось), плюс сопутствующие
     * бонусы: маленький — быстрее, большой — больше HP/урона/отбрасывания.
     */
    public int getSizeState() {
        return this.entityData.get(DATA_SIZE_STATE);
    }

    public void cycleSizeState() {
        if (!this.hasMutation("resize")) {
            return;
        }
        this.setSizeState((this.getSizeState() + 1) % 3);
    }

    /**
     * Ручной тумблер режима рейда "Уничтожителя" — переключается кнопкой на Вкладке 2 GUI рядом
     * с [x] у мутации "destroyer" (см. PigScreen, PigRaidModeTogglePacket). Пока включён, свинья
     * постоянно держится бомбардировщиком и бомбит по таймеру независимо от наличия цели снизу —
     * то есть ведёт себя как управляемая "летающая ТНТ-машина" для рейда построек.
     */
    /** Клиентская проверка: рисовать ли сейчас порталы Экстрасенса на модели. */
    public boolean isPsychicPortalActive() {
        return this.entityData.get(DATA_PSYCHIC_PORTAL);
    }

    public boolean isRaidMode() {
        return this.entityData.get(DATA_RAID_MODE);
    }

    public void toggleRaidMode() {
        if (!this.hasMutation("destroyer")) {
            return;
        }
        this.entityData.set(DATA_RAID_MODE, !this.isRaidMode());
    }

    public void setRaidMode(boolean raidMode) {
        this.entityData.set(DATA_RAID_MODE, raidMode);
    }

    public void setSizeState(int state) {
        this.entityData.set(DATA_SIZE_STATE, state);
        this.applySizeState();
    }

    /**
     * Публичная точка входа для эффекта мутации "Изменение размера" (см. Mutations.RESIZE):
     * вызывается при вводе/прокачке мутации, чтобы силу текущего размера пересчитать под
     * новый уровень, не трогая при этом сам выбранный режим (Маленький/Обычный/Большой).
     */
    public void refreshSizeState() {
        this.applySizeState();
    }

    private void applySizeState() {
        int state = this.getSizeState();
        // Уменьшение сохраняем прежним: на высоких уровнях маленькая свинья становится
        // заметно меньше, но остаётся в разумных пределах. Большой размер теперь растёт
        // значительно сильнее и на Lvl10 достигает примерно x5.6 от базового масштаба —
        // уже сопоставимо с небольшим домом жителя по высоте модели/хитбокса.
        int level = Math.max(1, this.getMutationLevel("resize"));

        double scaleBonus = state == 1
                ? -(0.06 + level * 0.04)
                : state == 2
                ? (0.08 + level * 0.45)
                : 0.0;
        double smallSpeedBonus = state == 1 ? level * 0.05 : 0.0;
        double bigHealthBonus = state == 2 ? level * 3.0 : 0.0;
        double bigDamageBonus = state == 2 ? level * 0.6 : 0.0;
        double bigKnockback = state == 2 ? 1.0 : 0.0; // отбрасывание — бинарный бонус, не растёт с уровнем

        this.setAttributeBonus(Attributes.SCALE, ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "resize_scale"),
                scaleBonus, AttributeModifier.Operation.ADD_VALUE);
        this.setAttributeBonus(Attributes.MOVEMENT_SPEED, ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "resize_speed"),
                smallSpeedBonus, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        this.setAttributeBonus(Attributes.MAX_HEALTH, ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "resize_health"),
                bigHealthBonus, AttributeModifier.Operation.ADD_VALUE);
        this.setAttributeBonus(Attributes.ATTACK_DAMAGE, ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "resize_damage"),
                bigDamageBonus, AttributeModifier.Operation.ADD_VALUE);
        this.setAttributeBonus(Attributes.ATTACK_KNOCKBACK, ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "resize_knockback"),
                bigKnockback, AttributeModifier.Operation.ADD_VALUE);
    }

    /**
     * Алхимик: контекстно помогает хозяину настоящими взрывными зельями.
     * Приоритеты: опасная среда -> критическое состояние -> подводное дыхание -> бой -> бег.
     * Одновременно бросается только одно зелье, после чего выдерживается кулдаун.
     */
    private int alchemistCooldown;
    private int alchemistRunTicks;

    private void tickAlchemist() {
        if (!this.hasMutation("alchemist")) {
            this.alchemistRunTicks = 0;
            this.alchemistCooldown = 0;
            return;
        }

        if (this.alchemistCooldown > 0) {
            this.alchemistCooldown--;
        }

        if (!(this.getOwner() instanceof Player owner) || !owner.isAlive() || owner.distanceToSqr(this) > 18.0D * 18.0D) {
            this.alchemistRunTicks = 0;
            return;
        }

        // Бег: считаем только устойчивое движение, а не одиночный тик спринта.
        double horizontalSpeedSqr = owner.getDeltaMovement().x * owner.getDeltaMovement().x
                + owner.getDeltaMovement().z * owner.getDeltaMovement().z;
        if (owner.isSprinting() && horizontalSpeedSqr > 0.0064D) {
            this.alchemistRunTicks++;
        } else {
            this.alchemistRunTicks = Math.max(0, this.alchemistRunTicks - 2);
        }

        if (this.alchemistCooldown > 0) {
            return;
        }

        String potion = chooseAlchemistPotion(owner);
        if (potion == null) {
            return;
        }

        int level = this.getMutationLevel("alchemist");
        if (throwAlchemistPotion(owner, potion)) {
            this.level().broadcastEntityEvent(this, (byte) 7);
            this.alchemistCooldown = alchemistCooldownTicks(level);
            if ("swiftness".equals(potion)) {
                this.alchemistRunTicks = 0;
            }
        }
    }

    /** Выбирает бафф именно под текущую ситуацию хозяина. */
    @Nullable
    private String chooseAlchemistPotion(Player owner) {
        // 1. Лава / горение — огнестойкость нужна немедленно.
        if (owner.isInLava() || owner.isOnFire() || owner.isInLava()) {
            return "fire_resistance";
        }

        // 2. Сильное ранение или свежий полученный урон — регенерация.
        boolean recentlyHurt = owner.getLastHurtByMob() != null
                && owner.tickCount - owner.getLastHurtByMobTimestamp() <= 30;
        if (owner.getHealth() <= owner.getMaxHealth() * 0.5F || recentlyHurt) {
            return "regeneration";
        }

        // 3. Под водой при расходовании воздуха — сверхдыхание.
        if (owner.isUnderWater() && owner.getAirSupply() < owner.getMaxAirSupply() - 20) {
            return "water_breathing";
        }

        // 4. Недавняя атака — сила перед продолжением боя.
        if (owner.getLastHurtMob() != null
                && owner.tickCount - owner.getLastHurtMobTimestamp() <= 15) {
            return "strength";
        }

        // 5. Долгий спринт — скорость.
        if (this.alchemistRunTicks >= 30) {
            return "swiftness";
        }

        return null;
    }

    /**
     * Создаёт настоящий Splash Potion и запускает его в сторону хозяина. ThrownPotion использует
     * обычный ванильный механизм попадания, поэтому визуал, звук и область действия совпадают с
     * обычным взрывным зельем из игры. PotionContents хранится как компонент предмета в 1.21.1.
     */
    private boolean throwAlchemistPotion(Player owner, String potionId) {
        if (this.level().isClientSide) {
            return false;
        }

        var potionHolder = switch (potionId) {
            case "swiftness" -> Potions.SWIFTNESS;
            case "regeneration" -> Potions.REGENERATION;
            case "fire_resistance" -> Potions.FIRE_RESISTANCE;
            case "water_breathing" -> Potions.WATER_BREATHING;
            case "strength" -> Potions.STRENGTH;
            default -> null;
        };
        if (potionHolder == null) {
            return false;
        }

        // Не используем готовые Potions.* напрямую: добавляем пользовательский эффект
        // с усилителем, равным уровню Алхимика - 1. Так Lvl2 даёт эффект II, Lvl3 — III и т.д.
        ItemStack potionStack = new ItemStack(Items.SPLASH_POTION);
        var effectHolder = switch (potionId) {
            case "swiftness" -> net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED;
            case "regeneration" -> net.minecraft.world.effect.MobEffects.REGENERATION;
            case "fire_resistance" -> net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE;
            case "water_breathing" -> net.minecraft.world.effect.MobEffects.WATER_BREATHING;
            case "strength" -> net.minecraft.world.effect.MobEffects.DAMAGE_BOOST;
            default -> null;
        };
        if (effectHolder == null) {
            return false;
        }
        int alchemistLevel = Math.max(1, this.getMutationLevel("alchemist"));
        int amplifier = alchemistLevel - 1;
        int duration = switch (potionId) {
            case "regeneration" -> 100;
            case "swiftness", "strength" -> 360;
            case "fire_resistance", "water_breathing" -> 2400;
            default -> 200;
        };
        PotionContents customContents = PotionContents.EMPTY.withEffectAdded(
                new net.minecraft.world.effect.MobEffectInstance(effectHolder, duration, amplifier, false, true, true));
        potionStack.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS, customContents);

        ThrownPotion thrownPotion = new ThrownPotion(this.level(), this);
        thrownPotion.setItem(potionStack);

        double dx = owner.getX() - this.getX();
        double dz = owner.getZ() - this.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        double targetY = owner.getY() + owner.getBbHeight() * 0.5D;
        double dy = targetY - this.getY();

        // Не слишком сильный бросок: зелье должно прилетать в хозяина на обычной игровой дистанции.
        thrownPotion.shoot(dx, dy + distance * 0.15D, dz, 0.7F, 2.0F);
        this.level().addFreshEntity(thrownPotion);
        this.playSound(net.minecraft.sounds.SoundEvents.SPLASH_POTION_THROW, 0.8F, 1.0F);
        return true;
    }

    /** Кулдаун алхимика: быстрее реагирует на высоких уровнях — от 10с до 3с. */
    public static int alchemistCooldownTicks(int level) {
        return Math.max(60, 220 - level * 32);
    }

    // --- НАЕЗДНИК: состояние седла ---
    // Отдельно от самой мутации: мутацию можно получить заранее, а физически оседлать (или снять
    // седло, убив свинью) — уже потом. Пока не оседлана — сесть верхом через mobInteract нельзя,
    // даже если мутация уже введена.

    public boolean isSaddled() {
        return this.entityData.get(DATA_SADDLED);
    }

    public void setSaddled(boolean saddled) {
        this.entityData.set(DATA_SADDLED, saddled);
    }

    // --- ПОЛЁТ ВЕРХОМ: явное разрешение на спешивание ---
    // Ванильное поведение — клавиша Sneak тут же ссаживает игрока с ЛЮБОГО ездового моба.
    // Но у "Полёта" Sneak теперь используется для СНИЖЕНИЯ высоты (см. travel() ниже), поэтому
    // такое авто-спешивание нужно заблокировать, иначе снижение было бы физически недостижимо —
    // игрок слетал бы с свиньи в тот же миг, что нажал Sneak, ещё до начала снижения.
    // Это делает PigEventHandlers.onPigDismountAttempt (слушает EntityMountEvent), а слезть теперь
    // можно только явно — выделенной клавишей "Спешиться (Полёт)" (по умолчанию X, настраивается
    // в Настройках управления, см. ClientKeyBindings), которая шлёт на сервер PigDismountPacket
    // и на секунду выставляет этот флаг, чтобы разрешить ИМЕННО этот один вызов stopRiding().
    private boolean dismountAllowed = false;

    // Сигналы управления полётом верхом приходят с клиента отдельным пакетом.
    // Это надёжнее, чем пытаться определить на сервере, нажата ли клавиша прыжка,
    // потому что Player.jumping недоступен здесь напрямую, а deltaMovement игрока
    // не является состоянием клавиши прыжка.
    private boolean flightUpPressed = false;
    private boolean flightDownPressed = false;

    // Эндерсвинья: аварийная телепортация при критически низком здоровье.
    private int enderEmergencyTeleportCooldown = 0;

    /** Разрешает следующий вызов stopRiding() пройти через PigEventHandlers.onPigDismountAttempt. */
    public void allowNextDismount() {
        this.dismountAllowed = true;
    }

    /** Считывает и сбрасывает флаг разрешения (одноразовый — сбрасывается сразу после проверки). */
    public boolean consumeDismountAllowance() {
        boolean was = this.dismountAllowed;
        this.dismountAllowed = false;
        return was;
    }

    /** Получает актуальное состояние клавиш подъёма/снижения от клиента. */
    public void setFlightInput(boolean up, boolean down) {
        if (!this.hasMutation("flight")) {
            this.flightUpPressed = false;
            this.flightDownPressed = false;
            return;
        }
        this.flightUpPressed = up;
        this.flightDownPressed = down;
    }

    /**
     * Множитель скорости полёта по уровню мутации "flight": Lvl1 = x1.2 ... Lvl5 (макс) = x2.0.
     * Используется и в travel() (вертикальная/горизонтальная скорость при полёте верхом),
     * и в описании мутации на Вкладке 2 GUI (см. Mutations.FLIGHT).
     */
    public static double flightSpeedMultiplier(int level) {
        return 1.0 + Math.max(0, level) * 0.2;
    }

    /** Синхронизирует скорость FlyingMoveControl с уровнем мутации "Полёт". */
    private void updateFlightSpeedAttribute() {
        AttributeInstance flyingSpeed = this.getAttribute(Attributes.FLYING_SPEED);
        if (flyingSpeed == null) {
            return;
        }

        flyingSpeed.removeModifier(FLIGHT_SPEED_BONUS_ID);
        if (this.hasMutation("flight")) {
            double multiplier = flightSpeedMultiplier(this.getMutationLevel("flight"));
            flyingSpeed.addTransientModifier(new AttributeModifier(
                    FLIGHT_SPEED_BONUS_ID,
                    0.3D * (multiplier - 1.0D),
                    AttributeModifier.Operation.ADD_VALUE
            ));
        }
    }

    /** Тики мутаций Tier 3, которые не имеют своего отдельного метода (Полёт, трансляция защит Наездника). */
    private void tickTier3Mutations() {
        // Полёт — это полноценный воздушный режим: 3D-навигация + FlyingMoveControl.
        // Само движение здесь не задаём вручную — этим занимается navigation/moveControl.
        // Здесь только гарантируем отсутствие гравитации, пока свинья имеет мутацию.
        if (this.hasMutation("flight") && !this.isVehicle()) {
            this.setNoGravity(true);
            this.fallDistance = 0.0F;
        }

        // Наездник + Плавание: пока хозяин верхом и свинья в воде, Прыть Дельфина передаётся и ему.
        // Остальные защитные мутации игрок получает здесь тоже — это постоянная часть состояния,
        // которую нельзя надёжно выразить только через события урона/эффектов.
        if (this.hasMutation("rider") && this.getControllingPassenger() instanceof Player rider) {
            if (this.hasMutation("overbreathing")) {
                rider.setAirSupply(rider.getMaxAirSupply());
            }

            if (this.hasMutation("fire_immunity")) {
                rider.clearFire();
                rider.setRemainingFireTicks(0);
                rider.setSharedFlagOnFire(false);
            }

            if (this.hasMutation("potion_resistance")) {
                rider.removeEffect(net.minecraft.world.effect.MobEffects.POISON);
                rider.removeEffect(net.minecraft.world.effect.MobEffects.WITHER);
            }

            if (this.hasMutation("gliding") || this.hasMutation("flight")) {
                rider.resetFallDistance();
            }

            if (this.hasMutation("aquatic") && this.isInWater()) {
                int level = this.getMutationLevel("aquatic");
                rider.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.DOLPHINS_GRACE, 60, Math.max(0, level - 1), false, false));
            }
        }
    }

    /**
     * Эндерсвинья: вместо пассивного иммунитета к снарядам уклоняется телепортацией.
     * Если на свинье есть Наездник, всадник телепортируется вместе с ней.
     */
    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        if (!this.level().isClientSide && this.hasMutation("angel") && this.angelCooldown <= 0
                && this.isAlive() && this.getHealth() - amount <= 0.0F) {
            this.angelCooldown = angelCooldownTicks(this.getMutationLevel("angel"));
            this.setHealth(Math.max(1.0F, this.getMaxHealth() * 0.35F));
            this.clearFire();
            this.removeAllEffects();
            this.level().broadcastEntityEvent(this, (byte) 35);
            if (this.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, this.getX(), this.getY() + 0.8D, this.getZ(),
                        40, 0.5D, 0.8D, 0.5D, 0.1D);
                serverLevel.playSound(null, this.blockPosition(), net.minecraft.sounds.SoundEvents.TOTEM_USE,
                        net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
            }
            return false;
        }

        if (this.hasMutation("enderpig")) {
            // Снаряд всегда полностью уклоняется: урон от любого projectile
            // никогда не проходит. Сначала ищем безопасную точку для телепортации.
            if (source.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE)) {
                if (!this.level().isClientSide) {
                    // Снаряд не может нанести Эндерсвинье урон ни при каких обстоятельствах:
                    // сначала пытаемся телепортироваться в безопасную точку, а если обычный
                    // поиск не нашёл её — используем расширенный аварийный поиск.
                    if (tryEnderPigProjectileTeleport(source) || forceEnderPigProjectileEscape(source)) {
                        this.level().broadcastEntityEvent(this, (byte) 46);
                    }
                    return false;
                }
            } else if (!this.level().isClientSide && source.getEntity() != null && this.random.nextFloat() < 0.5F) {
                // Сохраняем старое уклонение от части обычных ударов.
                if (tryEnderPigRandomTeleport(12.0D, 20.0D)) {
                    this.level().broadcastEntityEvent(this, (byte) 46);
                    return false;
                }
            }
        }
        return super.hurt(source, amount);
    }

    /** Телепортирует свинью в безопасную точку в направлении от летящего снаряда. */
    private boolean tryEnderPigProjectileTeleport(net.minecraft.world.damagesource.DamageSource source) {
        Entity direct = source.getDirectEntity();
        Vec3 away = Vec3.ZERO;

        if (direct instanceof Projectile projectile) {
            Vec3 velocity = projectile.getDeltaMovement();
            if (velocity.lengthSqr() > 1.0E-4D) {
                away = velocity.normalize().reverse();
            } else if (projectile.getOwner() != null) {
                away = this.position().subtract(projectile.getOwner().position());
            }
        } else if (source.getEntity() != null) {
            away = this.position().subtract(source.getEntity().position());
        }

        if (away.lengthSqr() < 1.0E-4D) {
            away = this.getForward().scale(-1.0D);
        } else {
            away = away.normalize();
        }

        double distance = 10.0D + this.random.nextDouble() * 10.0D;
        Vec3 side = new Vec3(-away.z, 0.0D, away.x);
        if (side.lengthSqr() > 1.0E-4D) {
            side = side.normalize();
        }

        Vec3[] candidates = new Vec3[]{
                this.position().add(away.scale(distance)),
                this.position().add(away.scale(distance)).add(side.scale(5.0D)),
                this.position().add(away.scale(distance)).add(side.scale(-5.0D)),
                this.position().add(away.scale(14.0D)).add(0.0D, 4.0D, 0.0D),
                this.position().add(away.scale(14.0D)).add(0.0D, -4.0D, 0.0D)
        };

        return tryTeleportToCandidate(candidates);
    }

    /**
     * Расширенный аварийный поиск для снаряда. Он перебирает много направлений и высот,
     * поэтому даже если первая группа кандидатов занята, Эндерсвинья всё равно пытается
     * найти реальную безопасную точку. Сам урон от снаряда при этом всегда блокируется в hurt().
     */
    private boolean forceEnderPigProjectileEscape(net.minecraft.world.damagesource.DamageSource source) {
        Entity direct = source.getDirectEntity();
        Vec3 away = this.getForward().scale(-1.0D);
        if (direct instanceof Projectile projectile) {
            Vec3 velocity = projectile.getDeltaMovement();
            if (velocity.lengthSqr() > 1.0E-6D) {
                away = velocity.normalize().reverse();
            } else if (projectile.getOwner() != null) {
                away = this.position().subtract(projectile.getOwner().position());
            }
        } else if (source.getEntity() != null) {
            away = this.position().subtract(source.getEntity().position());
        }
        if (away.lengthSqr() < 1.0E-6D) {
            away = new Vec3(1.0D, 0.0D, 0.0D);
        }
        away = away.normalize();

        for (int distance = 8; distance <= 32; distance += 4) {
            for (int i = 0; i < 16; i++) {
                double angle = (Math.PI * 2.0D * i) / 16.0D;
                Vec3 horizontal = new Vec3(away.x, 0.0D, away.z);
                if (horizontal.lengthSqr() < 1.0E-6D) {
                    horizontal = new Vec3(1.0D, 0.0D, 0.0D);
                } else {
                    horizontal = horizontal.normalize();
                }
                double cos = Math.cos(angle);
                double sin = Math.sin(angle);
                Vec3 rotated = new Vec3(
                        horizontal.x * cos - horizontal.z * sin,
                        0.0D,
                        horizontal.x * sin + horizontal.z * cos);
                Vec3 target = this.position().add(rotated.scale(distance));

                for (int vertical : new int[]{-8, -4, 0, 4, 8}) {
                    Vec3 candidate = target.add(0.0D, vertical, 0.0D);
                    if (tryTeleportToCandidate(new Vec3[]{candidate})) {
                        return true;
                    }
                }
            }
        }

        // Последняя попытка: несколько прямых ender-style смещений. Они выполняются
        // только после исчерпания безопасного поиска; урон в любом случае уже запрещён.
        Vec3[] emergency = new Vec3[]{
                this.position().add(away.scale(6.0D)).add(0.0D, 8.0D, 0.0D),
                this.position().add(away.scale(6.0D)).add(0.0D, -8.0D, 0.0D),
                this.position().add(away.scale(4.0D)),
                this.position().add(new Vec3(-away.z, 0.0D, away.x).normalize().scale(4.0D))
        };
        for (Vec3 target : emergency) {
            if (this.level().isEmptyBlock(BlockPos.containing(target))
                    && tryTeleportToCandidate(new Vec3[]{target})) {
                return true;
            }
        }
        return false;
    }

    /** Обычное эндеровское случайное уклонение. */
    private boolean tryEnderPigRandomTeleport(double minDistance, double maxDistance) {
        for (int i = 0; i < 12; i++) {
            double radius = minDistance + this.random.nextDouble() * (maxDistance - minDistance);
            double angle = this.random.nextDouble() * Math.PI * 2.0D;
            double x = this.getX() + Math.cos(angle) * radius;
            double y = this.getY() + (this.random.nextDouble() * 8.0D - 4.0D);
            double z = this.getZ() + Math.sin(angle) * radius;
            if (tryTeleportToCandidate(new Vec3[]{new Vec3(x, y, z)})) {
                return true;
            }
        }
        return false;
    }

    /**
     * Проверяет безопасную точку, телепортирует туда свинью и всех её пассажиров.
     */
    private boolean tryTeleportToCandidate(Vec3[] candidates) {
        for (Vec3 target : candidates) {
            BlockPos blockPos = BlockPos.containing(target);
            if (this.level() instanceof ServerLevel serverLevel && !serverLevel.hasChunkAt(blockPos)) {
                continue;
            }

            var fluidState = this.level().getFluidState(blockPos);
            var blockState = this.level().getBlockState(blockPos);
            if (fluidState.is(FluidTags.LAVA)
                    || blockState.is(net.minecraft.world.level.block.Blocks.FIRE)
                    || blockState.is(net.minecraft.world.level.block.Blocks.SOUL_FIRE)) {
                continue;
            }

            var box = this.getBoundingBox().move(
                    target.x - this.getX(), target.y - this.getY(), target.z - this.getZ());
            if (!this.level().noCollision(this, box)
                    || this.level().getBlockCollisions(this, box).iterator().hasNext()) {
                continue;
            }

            double oldX = this.getX();
            double oldY = this.getY();
            double oldZ = this.getZ();
            this.teleportTo(target.x, target.y, target.z);
            this.setDeltaMovement(Vec3.ZERO);
            this.setNoGravity(this.wantsNoGravity());

            // Пассажиров переносим той же телепортацией. Оставляем их пассажирами;
            // следующий tick дополнительно поставит их в обычную точку посадки.
            double dx = target.x - oldX;
            double dy = target.y - oldY;
            double dz = target.z - oldZ;
            for (Entity passenger : this.getPassengers()) {
                passenger.teleportTo(passenger.getX() + dx, passenger.getY() + dy, passenger.getZ() + dz);
            }
            return true;
        }
        return false;
    }

    /**
     * При критически низком здоровье Эндерсвинья пытается уйти от ближайших монстров,
     * снарядов и огня/лавы в безопасную точку.
     */
    private void tickEnderPigEmergencyEscape() {
        if (!this.hasMutation("enderpig") || !this.isAlive() || this.level().isClientSide) {
            return;
        }

        if (this.enderEmergencyTeleportCooldown > 0) {
            this.enderEmergencyTeleportCooldown--;
        }

        double criticalHealth = this.getMaxHealth() * 0.25D;
        if (this.getHealth() > criticalHealth || this.enderEmergencyTeleportCooldown > 0) {
            return;
        }

        List<net.minecraft.world.entity.LivingEntity> nearbyLiving = this.level().getEntitiesOfClass(
                net.minecraft.world.entity.LivingEntity.class,
                this.getBoundingBox().inflate(14.0D),
                living -> living.isAlive() && living instanceof net.minecraft.world.entity.monster.Enemy);
        List<net.minecraft.world.entity.monster.Enemy> enemies = new java.util.ArrayList<>();
        for (net.minecraft.world.entity.LivingEntity living : nearbyLiving) {
            enemies.add((net.minecraft.world.entity.monster.Enemy) living);
        }
        List<Projectile> projectiles = this.level().getEntitiesOfClass(
                Projectile.class,
                this.getBoundingBox().inflate(12.0D),
                projectile -> projectile.isAlive());

        boolean inDangerousFluid = this.isInLava()
                || this.level().getFluidState(this.blockPosition()).is(FluidTags.LAVA);
        boolean dangerNearby = !enemies.isEmpty() || !projectiles.isEmpty() || inDangerousFluid;
        if (!dangerNearby) {
            return;
        }

        Vec3 threatCenter = Vec3.ZERO;
        int threatCount = 0;
        for (net.minecraft.world.entity.monster.Enemy enemy : enemies) {
            net.minecraft.world.entity.LivingEntity livingEnemy = (net.minecraft.world.entity.LivingEntity) enemy;
            threatCenter = threatCenter.add(livingEnemy.position());
            threatCount++;
        }
        for (Projectile projectile : projectiles) {
            threatCenter = threatCenter.add(projectile.position());
            threatCount++;
        }

        Vec3 away;
        if (threatCount > 0) {
            threatCenter = threatCenter.scale(1.0D / threatCount);
            away = this.position().subtract(threatCenter);
        } else {
            away = new Vec3(this.random.nextDouble() - 0.5D, 0.0D, this.random.nextDouble() - 0.5D);
        }
        if (away.lengthSqr() < 1.0E-4D) {
            away = this.getForward();
        }
        away = away.normalize();

        Vec3 side = new Vec3(-away.z, 0.0D, away.x);
        if (side.lengthSqr() > 1.0E-4D) {
            side = side.normalize();
        }

        Vec3 base = this.position().add(away.scale(28.0D));
        Vec3[] candidates = new Vec3[]{
                base,
                base.add(side.scale(8.0D)),
                base.add(side.scale(-8.0D)),
                base.add(away.scale(8.0D)).add(0.0D, 5.0D, 0.0D),
                base.add(away.scale(8.0D)).add(0.0D, -5.0D, 0.0D)
        };

        // Кандидат считается безопасным только если вокруг него нет ближайших монстров
        // и снарядов, и сам блок не опасен.
        for (Vec3 target : candidates) {
            boolean farFromEnemies = true;
            for (net.minecraft.world.entity.monster.Enemy enemy : enemies) {
                net.minecraft.world.entity.LivingEntity livingEnemy = (net.minecraft.world.entity.LivingEntity) enemy;
                if (livingEnemy.distanceToSqr(target.x, target.y, target.z) < 16.0D * 16.0D) {
                    farFromEnemies = false;
                    break;
                }
            }
            if (!farFromEnemies) continue;

            if (tryTeleportToCandidate(new Vec3[]{target})) {
                this.level().broadcastEntityEvent(this, (byte) 46);
                this.enderEmergencyTeleportCooldown = 100;
                return;
            }
        }
    }

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
        if ((this.hasMutation("overbreathing") || this.hasMutation("ethereal"))
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
        // Планирование или Полёт — без урона от падения
        if (this.hasMutation("gliding") || this.hasMutation("flight")) {
            return false;
        }
        return super.causeFallDamage(fallDistance, multiplier, source);
    }

    // --- НАЕЗДНИК: управление свиньёй верхом ---

    @Override
    @Nullable
    public net.minecraft.world.entity.LivingEntity getControllingPassenger() {
        // Управлять может только хозяин, и только если свинья оседлана и мутация введена —
        // иначе, даже сидя верхом (например, случайно через /ride), игрок её не поведёт.
        if (this.isSaddled() && this.hasMutation("rider") && this.getFirstPassenger() instanceof Player player
                && this.isOwnedBy(player)) {
            return player;
        }
        return null; // как и у ванильного Pig — без седока-хозяина никто её не "ведёт"
    }

    @Override
    public void travel(net.minecraft.world.phys.Vec3 travelVector) {
        if (this.isOrderedToSit()) {
            this.navigation.stop();
            this.setDeltaMovement(Vec3.ZERO);
            this.setNoGravity(this.wantsNoGravity());
            this.fallDistance = 0.0F;
            this.walkAnimation.setSpeed(0.0F);
            return;
        }

        if (this.isAlive() && this.isVehicle() && this.getControllingPassenger() instanceof Player rider) {
            this.setYRot(rider.getYRot());
            this.yRotO = this.getYRot();
            this.setXRot(rider.getXRot() * 0.5F);
            this.setRot(this.getYRot(), this.getXRot());
            this.yBodyRot = this.getYRot();
            this.yHeadRot = this.yBodyRot;

            float strafe = rider.xxa * 0.5F;
            float forward = rider.zza;
            if (forward < 0.0F) {
                forward *= 0.25F;
            }

            if (this.hasMutation("flight")) {
                this.setNoGravity(true);
                this.fallDistance = 0.0F;

                int level = this.getMutationLevel("flight");
                double speed = this.getAttributeValue(Attributes.MOVEMENT_SPEED)
                        * flightSpeedMultiplier(level);
                this.setSpeed((float) speed);

                float yaw = rider.getYRot() * ((float) Math.PI / 180.0F);
                double sinYaw = Math.sin(yaw);
                double cosYaw = Math.cos(yaw);

                double moveX = (-sinYaw * forward) + (cosYaw * strafe);
                double moveZ = (cosYaw * forward) + (sinYaw * strafe);
                double horizontalInputLength = Math.sqrt(moveX * moveX + moveZ * moveZ);
                if (horizontalInputLength > 1.0D) {
                    moveX /= horizontalInputLength;
                    moveZ /= horizontalInputLength;
                }

                // Не обнуляем скорость при отпускании клавиш: летающая свинья сохраняет
                // инерцию и плавно тормозит, вместо мгновенной остановки.
                Vec3 current = this.getDeltaMovement();
                double desiredX = moveX * speed;
                double desiredZ = moveZ * speed;
                double desiredY = 0.0D;
                double verticalSpeed = 0.35D * flightSpeedMultiplier(level);
                if (this.flightUpPressed && !this.flightDownPressed) {
                    desiredY = verticalSpeed;
                } else if (this.flightDownPressed && !this.flightUpPressed) {
                    desiredY = -verticalSpeed;
                }

                // Разгон/торможение по горизонтали и вертикали. Это сохраняет ощущение
                // полёта и устраняет резкий стоп, который был при прямом setDeltaMovement().
                double inputAccel = horizontalInputLength > 0.01D ? 0.28D : 0.10D;
                double verticalAccel = (Math.abs(desiredY) > 0.001D) ? 0.30D : 0.10D;

                double nextX = current.x + (desiredX - current.x) * inputAccel;
                double nextZ = current.z + (desiredZ - current.z) * inputAccel;
                double nextY = current.y + (desiredY - current.y) * verticalAccel;

                if (horizontalInputLength <= 0.01D) {
                    nextX *= 0.96D;
                    nextZ *= 0.96D;
                }
                if (Math.abs(desiredY) <= 0.001D) {
                    nextY *= 0.92D;
                }

                Vec3 nextMovement = new Vec3(nextX, nextY, nextZ);
                this.setDeltaMovement(nextMovement);
                this.move(net.minecraft.world.entity.MoverType.SELF, nextMovement);

                // Vanilla автоматически обновляет эти данные во время обычного travel(),
                // но мы обходим его ради полноценного 3D-управления. Обновляем анимацию
                // вручную, чтобы ноги свиньи продолжали двигаться во время полёта верхом.
                double horizontalDistance = Math.sqrt(nextMovement.x * nextMovement.x + nextMovement.z * nextMovement.z);
                float animationSpeed = (float) Math.min(1.0D, horizontalDistance * 4.0D);
                if (animationSpeed > 0.01F) {
                    this.walkAnimation.update(animationSpeed, 1.0F);
                } else {
                    this.walkAnimation.setSpeed(0.0F);
                }
                return;
            }

            this.setNoGravity(false);
            this.setSpeed((float) this.getAttributeValue(Attributes.MOVEMENT_SPEED));
            super.travel(new Vec3(strafe, travelVector.y, forward));
            return;
        }

        this.setNoGravity(this.wantsNoGravity());
        super.travel(travelVector);
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

        if ("flight".equals(mutationId)) {
            updateFlightSpeedAttribute();
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
        updateFlightSpeedAttribute();
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
                    player.sendSystemMessage(Component.translatable("message.pig_mod.level_up", this.getPigLevel(), this.getSkillPoints()));
                }
            }
        }
        // Если уровень уперся в потолок, остаток опыта не хранит смысла — обнуляем, чтобы не копился зря.
        this.entityData.set(DATA_EXP, this.getPigLevel() >= MAX_LEVEL ? 0 : newExp);
    }

    // Бонусы характеристик больше НЕ выдаются за обычный уровень свиньи.
    // Все настоящие ХП/урон/броня должны приходить только от соответствующих мутаций.
    // Удаляем старые модификаторы legacy-системы, если они остались на сущности из старой версии мода.
    private void updateStatsForLevel() {
        AttributeInstance healthAttr = this.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttr != null) {
            healthAttr.removeModifier(HEALTH_BONUS_ID);
        }

        AttributeInstance damageAttr = this.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damageAttr != null) {
            damageAttr.removeModifier(DAMAGE_BONUS_ID);
        }

        if (this.getHealth() > this.getMaxHealth()) {
            this.setHealth(this.getMaxHealth());
        }
    }

    // --- ИИ И ПОВЕДЕНИЕ ---

    @Override
    public boolean isFood(ItemStack stack) {
        // Боевые свиньи едят весь ванильный набор еды из minecraft:pig_food
        // (морковь, картофель и свёклу), а также наш специальный деликатес.
        return stack.is(ItemTags.PIG_FOOD) || stack.is(ModItems.PIG_TREAT.get());
    }

    /**
     * Сколько голода восстанавливает конкретная порция еды.
     * Для обычной свиной еды используем её ванильную питательность и переводим
     * её в масштаб нашей собственной шкалы: 1 nutrition = 10 единиц голода свиньи.
     */
    private int getPigFoodHungerRestore(ItemStack stack) {
        if (stack.is(ModItems.PIG_TREAT.get())) {
            return 40;
        }

        if (!stack.is(ItemTags.PIG_FOOD)) {
            return 0;
        }

        FoodProperties food = stack.getItem().getFoodProperties(stack, this);
        return food == null ? 0 : Math.max(1, food.nutrition() * 10);
    }

    /**
     * Переключает свинью между обычной наземной навигацией и полноценной 3D-навигацией.
     * FlyingPathNavigation использует FlyNodeEvaluator и умеет строить путь в объёме,
     * поэтому свинья может огибать стены, крыши и другие препятствия по высоте.
     */
    private void updateFlightMovementMode() {
        boolean wantsFlight = this.hasMutation("flight");

        if (wantsFlight) {
            if (!(this.navigation instanceof FlyingPathNavigation)) {
                this.navigation.stop();
                FlyingPathNavigation flyingNavigation = new FlyingPathNavigation(this, this.level());
                flyingNavigation.setCanOpenDoors(false);
                flyingNavigation.setCanPassDoors(false);
                this.navigation = flyingNavigation;
            }
            if (!(this.moveControl instanceof FlyingMoveControl)) {
                this.moveControl = new FlyingMoveControl(this, 20, true);
            }
            this.setNoGravity(true);
        } else {
            if (!(this.navigation instanceof GroundPathNavigation)) {
                this.navigation.stop();
                this.navigation = new GroundPathNavigation(this, this.level());
            }
            if (!(this.moveControl.getClass().equals(MoveControl.class))) {
                this.moveControl = new MoveControl(this);
            }
            this.setNoGravity(false);
            this.flightUpPressed = false;
            this.flightDownPressed = false;
        }
    }

    /**
     * Mob создаёт навигацию в конструкторе ещё до того, как загружены данные мутаций,
     * поэтому по умолчанию стартуем с обычной наземной навигации, а режим Полёта включаем
     * после загрузки/активации мутации.
     */
    @Override
    protected PathNavigation createNavigation(Level level) {
        return new GroundPathNavigation(this, level);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, new SitWhenOrderedToGoal(this));
        this.goalSelector.addGoal(3, new FlightFollowOwnerGoal(this, 1.25D));
        this.goalSelector.addGoal(3, new GroundAwareFollowOwnerGoal(this, 1.25D, 10.0F, 2.0F));
        // Жёсткая граница рабочей области — приоритет выше прогулки, иначе свинья уходит гулять наружу.
        this.goalSelector.addGoal(3, new WorkAreaReturnGoal(this));
        this.goalSelector.addGoal(4, new ArcherGoal(this)); // приоритет выше ближнего боя — если archer не разблокирован, canUse() вернёт false и ход перейдёт к MeleeAttackGoal
        this.goalSelector.addGoal(4, new CommandMeleeAttackGoal(this, 1.2D, true));
        // Обычная прогулка остаётся для всех свиней без Полёта. Для Полёта она
        // выключается, а вместо неё включается воздушная 3D-прогулка ниже.
        this.goalSelector.addGoal(5, new GroundAwareRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(5, new FlightAwareRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        // Рабочие профессии Tier 2 — добавлены всегда, но внутри сами проверяют hasMutation(...)
        // и просто не активируются, если мутации нет. Виртуальный инструмент нужен только для
        // расчёта дропа (Block.getDrops), физически в лапах/зубах свиньи не отображается.
        // Параметры: (мутация, что искать, инструмент, пересаживать?, рубить дерево мгновенно
        //             целиком?, умеет копать препятствия?)
        this.goalSelector.addGoal(4, new ResourceGatherGoal(this, "lumberjack",
                state -> state.is(BlockTags.LOGS), new ItemStack(Items.DIAMOND_AXE),
                false, true, false)); // мгновенно рубит всё дерево целиком (TreeCapitator-стиль)
        this.goalSelector.addGoal(4, new ResourceGatherGoal(this, "miner",
                TameablePigEntity::isOre, new ItemStack(Items.DIAMOND_PICKAXE),
                false, false, true)); // умеет прорывать путь через камень к руде
        this.goalSelector.addGoal(4, new ResourceGatherGoal(this, "farmer",
                state -> state.getBlock() instanceof net.minecraft.world.level.block.CropBlock crop && crop.isMaxAge(state),
                new ItemStack(Items.DIAMOND_HOE), true, false, false));

        this.targetSelector.addGoal(1, new CommandMonsterTargetGoal(this));
        this.targetSelector.addGoal(1, new WorkGuardTargetGoal(this));
        this.targetSelector.addGoal(2, new OwnerHurtByTargetGoal(this));
        this.targetSelector.addGoal(3, new OwnerHurtTargetGoal(this));
        this.targetSelector.addGoal(4, new HurtByTargetGoal(this));
    }

    /** Обычная ближняя атака работает для защиты хозяина и для режима "Атаковать монстров", но
     * полностью выключается в режиме "Сидеть". */
    private static class CommandMeleeAttackGoal extends MeleeAttackGoal {
        private final TameablePigEntity pig;

        public CommandMeleeAttackGoal(TameablePigEntity pig, double speedModifier, boolean followingTargetEvenIfNotSeen) {
            super(pig, speedModifier, followingTargetEvenIfNotSeen);
            this.pig = pig;
        }

        @Override
        public boolean canUse() {
            return !pig.isOrderedToSit() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return !pig.isOrderedToSit() && super.canContinueToUse();
        }
    }

    /** В режиме "Атаковать монстров" ищем ближайшего враждебного моба. Игроки и хозяин сюда не попадают. */
    private static class CommandMonsterTargetGoal extends NearestAttackableTargetGoal<LivingEntity> {
        private final TameablePigEntity pig;

        public CommandMonsterTargetGoal(TameablePigEntity pig) {
            super(pig, LivingEntity.class, true, target -> target instanceof Enemy
                    && target != pig
                    && target != pig.getOwner()
                    && !pig.isWhitelisted(target.getType()));
            this.pig = pig;
        }

        @Override
        public boolean canUse() {
            return pig.isAttackCommand() && !pig.isOrderedToSit() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return pig.isAttackCommand() && !pig.isOrderedToSit() && super.canContinueToUse();
        }
    }

    /** Охрана территории: без рабочих мутаций в команде "Работать" атакует всё живое в области,
     * кроме хозяина и мобов из белого списка. Без заданной области цель не включается. */
    private static class WorkGuardTargetGoal extends NearestAttackableTargetGoal<LivingEntity> {
        private final TameablePigEntity pig;
        public WorkGuardTargetGoal(TameablePigEntity pig) {
            super(pig, LivingEntity.class, true, target -> target != pig
                    && target != pig.getOwner()
                    && !(target instanceof Player)
                    // Белый список — главная причина, по которой цель отбрасывается.
                    && !pig.isWhitelisted(target.getType())
                    // Область задана -> охраняем только её. Область не задана -> свинья
                    // работает где угодно и трогает только враждебных мобов.
                    && (pig.hasWorkArea()
                            ? pig.isInsideWorkArea(target.blockPosition())
                            : target instanceof Enemy));
            this.pig = pig;
        }
        @Override public boolean canUse() {
            return pig.isWorkCommand() && !pig.hasWorkMutation()
                    && !pig.isOrderedToSit() && super.canUse();
        }
        @Override public boolean canContinueToUse() {
            LivingEntity target=pig.getTarget();
            return pig.isWorkCommand() && !pig.hasWorkMutation()
                    && !pig.isOrderedToSit() && target != null && target.isAlive()
                    && !pig.isWhitelisted(target.getType())
                    && (!pig.hasWorkArea() || pig.isInsideWorkArea(target.blockPosition()));
        }
    }

    /**
     * Возврат в рабочую область. Раньше границы области проверялись только внутри
     * ResourceGatherGoal, поэтому обычная прогулка/следование спокойно выносили свинью
     * наружу и она там и оставалась — со стороны это выглядело как "свинья игнорирует радиус".
     * Этот голл имеет более высокий приоритет, чем прогулка, и физически тянет свинью назад.
     */
    private static class WorkAreaReturnGoal extends Goal {
        private final TameablePigEntity pig;
        private BlockPos destination;
        private int recalcCooldown;

        WorkAreaReturnGoal(TameablePigEntity pig) {
            this.pig = pig;
            this.setFlags(java.util.EnumSet.of(Goal.Flag.MOVE));
        }

        private boolean outside() {
            return pig.isWorkCommand() && pig.hasWorkArea() && !pig.isOrderedToSit()
                    && !pig.isInsideWorkArea(pig.blockPosition());
        }

        @Override public boolean canUse() { return outside(); }
        @Override public boolean canContinueToUse() { return outside(); }

        @Override
        public void start() {
            this.destination = pig.clampToWorkArea(pig.blockPosition());
            this.recalcCooldown = 0;
        }

        @Override
        public void stop() {
            this.destination = null;
            pig.getNavigation().stop();
        }

        @Override
        public void tick() {
            if (--recalcCooldown > 0 && !pig.getNavigation().isDone()) {
                return;
            }
            recalcCooldown = 20;
            this.destination = pig.clampToWorkArea(pig.blockPosition());
            if (this.destination != null) {
                pig.getNavigation().moveTo(destination.getX() + 0.5D, destination.getY(), destination.getZ() + 0.5D, 1.15D);
            }
        }
    }

    /** Наземная прогулка отключается на время действия Полёта. */
    private static class GroundAwareFollowOwnerGoal extends FollowOwnerGoal {
        private final TameablePigEntity pig;

        public GroundAwareFollowOwnerGoal(TameablePigEntity pig, double speed, float startDistance, float stopDistance) {
            super(pig, speed, startDistance, stopDistance);
            this.pig = pig;
        }

        @Override
        public boolean canUse() {
            // bomberMode: пока свинья летит бомбить (авто или ручной Рейд), этот голл не должен
            // тащить/телепортировать её обратно к хозяину — именно это и вызывало баг с телепортом.
            return !this.pig.hasMutation("flight") && !this.pig.isOrderedToSit() && !this.pig.bomberMode
                    && this.pig.shouldFollowOwner()
                    && !(this.pig.isAttackCommand() && this.pig.getTarget() != null)
                    && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return !this.pig.hasMutation("flight") && !this.pig.isOrderedToSit() && !this.pig.bomberMode
                    && this.pig.shouldFollowOwner()
                    && !(this.pig.isAttackCommand() && this.pig.getTarget() != null)
                    && super.canContinueToUse();
        }
    }

    private static class FlightFollowOwnerGoal extends Goal {
        private final TameablePigEntity pig;
        private final double speedModifier;
        private int recalcCooldown;

        public FlightFollowOwnerGoal(TameablePigEntity pig, double speedModifier) {
            this.pig = pig;
            this.speedModifier = speedModifier;
            this.setFlags(java.util.EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Nullable
        private Player getOwnerPlayer() {
            LivingEntity owner = this.pig.getOwner();
            return owner instanceof Player player && player.isAlive() ? player : null;
        }

        @Override
        public boolean canUse() {
            if (!this.pig.hasMutation("flight") || this.pig.isOrderedToSit() || this.pig.isVehicle() || this.pig.bomberMode
                    || !this.pig.shouldFollowOwner()
                    || (this.pig.isAttackCommand() && this.pig.getTarget() != null)) return false;
            Player owner = this.getOwnerPlayer();
            return owner != null && this.pig.isOwnedBy(owner) && this.pig.distanceToSqr(owner) > 9.0D;
        }

        @Override
        public boolean canContinueToUse() {
            if (!this.pig.hasMutation("flight") || this.pig.isOrderedToSit() || this.pig.isVehicle() || this.pig.bomberMode
                    || !this.pig.shouldFollowOwner()
                    || (this.pig.isAttackCommand() && this.pig.getTarget() != null)) return false;
            Player owner = this.getOwnerPlayer();
            return owner != null && this.pig.isOwnedBy(owner) && this.pig.distanceToSqr(owner) > 4.0D;
        }

        @Override
        public void start() { this.recalcCooldown = 0; }

        @Override
        public void stop() {
            this.pig.navigation.stop();
            this.recalcCooldown = 0;
        }

        @Override
        public void tick() {
            Player owner = this.getOwnerPlayer();
            if (owner == null || this.pig.isOrderedToSit()) {
                this.pig.navigation.stop();
                return;
            }

            this.pig.getLookControl().setLookAt(owner, 30.0F, 30.0F);

            double distanceSqr = this.pig.distanceToSqr(owner);

            // Как у обычных приручаемых мобов: если хозяин ушёл очень далеко,
            // не заставляем 3D pathfinding строить бесконечный маршрут через полмира.
            // Ищем безопасную точку рядом с хозяином и телепортируем свинью туда.
            if (distanceSqr > 32.0D * 32.0D) {
                if (this.tryTeleportNearOwner(owner)) {
                    this.pig.navigation.stop();
                    this.recalcCooldown = 10;
                    return;
                }
            }

            if (--this.recalcCooldown <= 0) {
                this.recalcCooldown = 10;
                if (this.pig.navigation instanceof FlyingPathNavigation) {
                    this.pig.navigation.moveTo(owner, this.speedModifier);
                }
            }
        }

        private boolean tryTeleportNearOwner(Player owner) {
            // Несколько фиксированных вариантов дают воспроизводимое поведение без
            // случайных телепортов внутрь стен. Предпочитаем точку немного позади/сбоку
            // от хозяина и на той же высоте.
            double yaw = Math.toRadians(owner.getYRot());
            double forwardX = -Math.sin(yaw);
            double forwardZ = Math.cos(yaw);

            double[][] offsets = {
                    {-4.0D, 0.0D, -4.0D},
                    { 4.0D, 0.0D, -4.0D},
                    {-4.0D, 0.0D,  4.0D},
                    { 4.0D, 0.0D,  4.0D},
                    {-2.5D, 1.5D,  0.0D},
                    { 2.5D, 1.5D,  0.0D},
                    { 0.0D, 1.5D, -2.5D},
                    { 0.0D, 1.5D,  2.5D},
                    { 0.0D,-1.5D, -2.5D},
                    { 0.0D,-1.5D,  2.5D}
            };

            for (double[] offset : offsets) {
                // Первую координату смещаем относительно взгляда хозяина, вторую — поперёк.
                double side = offset[0];
                double forward = offset[2];
                double x = owner.getX() + forwardX * forward - forwardZ * side;
                double z = owner.getZ() + forwardZ * forward + forwardX * side;
                double y = owner.getY() + offset[1];

                var box = this.pig.getBoundingBox().move(
                        x - this.pig.getX(),
                        y - this.pig.getY(),
                        z - this.pig.getZ());

                if (this.pig.level().noCollision(this.pig, box)
                        && !this.pig.level().getBlockCollisions(this.pig, box).iterator().hasNext()) {
                    this.pig.teleportTo(x, y, z);
                    this.pig.setDeltaMovement(Vec3.ZERO);
                    this.pig.setNoGravity(true);
                    this.pig.getNavigation().stop();
                    return true;
                }
            }
            return false;
        }
    }

    private static class GroundAwareRandomStrollGoal extends WaterAvoidingRandomStrollGoal {
        private final TameablePigEntity pig;

        public GroundAwareRandomStrollGoal(TameablePigEntity pig, double speedModifier) {
            super(pig, speedModifier);
            this.pig = pig;
        }

        @Override
        public boolean canUse() {
            return !this.pig.hasMutation("flight") && !this.pig.bomberMode && this.pig.shouldFollowOwner() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return !this.pig.hasMutation("flight") && !this.pig.bomberMode && this.pig.shouldFollowOwner() && super.canContinueToUse();
        }
    }

    /**
     * Для мутации Полёт используем ванильную воздушную wandering-логику.
     * WaterAvoidingRandomFlyingGoal сама выбирает 3D-точку и отдаёт её FlyingPathNavigation,
     * а мы лишь выключаем её, когда Полёта нет или свиньёй управляет игрок.
     */
    private static class FlightAwareRandomStrollGoal extends WaterAvoidingRandomFlyingGoal {
        private final TameablePigEntity pig;

        public FlightAwareRandomStrollGoal(TameablePigEntity pig, double speedModifier) {
            super(pig, speedModifier);
            this.pig = pig;
        }

        @Override
        public boolean canUse() {
            return this.pig.hasMutation("flight")
                    && !this.pig.isVehicle()
                    && !this.pig.isTame()
                    && !this.pig.isOrderedToSit()
                    && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return this.pig.hasMutation("flight")
                    && !this.pig.isVehicle()
                    && !this.pig.isTame()
                    && !this.pig.isOrderedToSit()
                    && super.canContinueToUse();
        }
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
            
            if (itemstack.is(ModItems.CONTROL_TABLET.get())) {
                if (!this.level().isClientSide) {
                    PigControlTabletItem.selectPig(itemstack, this);
                    player.displayClientMessage(Component.translatable("message.pig_mod.tablet.pig_selected"), true);
                }
                return InteractionResult.sidedSuccess(this.level().isClientSide);
            }

            // Посох Курьера — если у него есть привязанный сундук, переносим привязку на свинью
            // (работает независимо от того, есть ли уже мутация "courier" — привязать можно заранее)
            if (itemstack.getItem() instanceof CourierStaffItem) {
                BlockPos chestPos = CourierStaffItem.getBoundChest(itemstack);
                if (chestPos != null) {
                    if (!this.level().isClientSide) {
                        this.setCourierChestPos(chestPos);
                        player.sendSystemMessage(Component.translatable("message.pig_mod.courier_chest_bound"));
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

            // Деликатес теперь одновременно кормит свинью и используется как предмет размножения.
            // Сердечки для любви появляются только когда рядом есть совместимый по полу взрослый
            // боевой хряк/свинья. Само ограничение пары дополнительно проверяется в canMate().
            if (itemstack.is(ModItems.PIG_TREAT.get())) {
                if (!this.level().isClientSide) {
                    if (!this.isBaby() && this.canFallInLove() && hasCompatibleMateNearby()) {
                        this.setInLove(player);
                    }
                    this.restoreHunger(40);
                    this.heal(5.0F);
                    this.addPigExp(5);
                    if (!player.getAbilities().instabuild) {
                        itemstack.shrink(1);
                    }
                    this.level().broadcastEntityEvent(this, (byte) 7);
                }
                return InteractionResult.sidedSuccess(this.level().isClientSide);
            }

            // Обычная свиная еда тоже восстанавливает нашу шкалу голода.
            // В качестве дополнительных эффектов остаётся только стандартное размножение:
            // корм можно использовать для запуска любви у подходящей пары.
            if (itemstack.is(ItemTags.PIG_FOOD)) {
                if (!this.level().isClientSide) {
                    boolean fed = this.getHunger() < this.getMaxHunger();
                    boolean bred = !this.isBaby() && this.canFallInLove() && hasCompatibleMateNearby();

                    if (fed) {
                        this.restoreHunger(this.getPigFoodHungerRestore(itemstack));
                    }
                    if (bred) {
                        this.setInLove(player);
                    }

                    // Не расходуем еду впустую, если голод полон и рядом нет подходящего
                    // партнёра для размножения.
                    if (fed || bred) {
                        if (!player.getAbilities().instabuild) {
                            itemstack.shrink(1);
                        }
                        this.level().broadcastEntityEvent(this, (byte) 7);
                    }
                }
                return InteractionResult.sidedSuccess(this.level().isClientSide);
            }

            // Свиночеловек — ПКМ Ножницами снимает ВСЮ надетую броню (если она есть), не трогая
            // предмет в "руке" свиньи (слот MAINHAND — оружие/инструмент снимается по-другому, это
            // логика ниже). Проверяется ДО общей экипировки, иначе сами Ножницы попали бы в лапы
            // свинье как оружие вместо того, чтобы сработать как инструмент для снятия брони.
            if (this.hasMutation("pigman") && itemstack.is(net.minecraft.world.item.Items.SHEARS)
                    && hand == InteractionHand.MAIN_HAND && !player.isShiftKeyDown()) {
                if (!this.level().isClientSide) {
                    boolean removedAny = false;
                    for (net.minecraft.world.entity.EquipmentSlot slot : new net.minecraft.world.entity.EquipmentSlot[]{
                            net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
                            net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET}) {
                        ItemStack armorPiece = this.getItemBySlot(slot);
                        if (!armorPiece.isEmpty()) {
                            this.spawnAtLocation(armorPiece);
                            this.setItemSlot(slot, ItemStack.EMPTY);
                            removedAny = true;
                        }
                    }
                    if (removedAny) {
    this.playSound(net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_GENERIC.value(), 1.0F, 1.0F);
    player.sendSystemMessage(Component.translatable("message.pig_mod.armor_removed"));
} else {
                        player.sendSystemMessage(Component.translatable("message.pig_mod.no_armor"));
                    }
                }
                return InteractionResult.sidedSuccess(this.level().isClientSide);
            }

            // Свиночеловек — ПКМ бронёй/оружием (без Shift, чтобы не конфликтовать с открытием GUI)
            // экипирует предмет в соответствующий слот; ванильная система атрибутов предметов сама
            // применит бонусы урона/брони к свинье, никакого ручного пересчёта не требуется.
            // Визуально свинья остаётся четвероногой — см. заметку в Mutations.PIGMAN.
            if (this.hasMutation("pigman") && !itemstack.isEmpty() && hand == InteractionHand.MAIN_HAND
                    && !player.isShiftKeyDown()) {
                net.minecraft.world.entity.EquipmentSlot slot =
                        this.getEquipmentSlotForItem(itemstack);
                if (!this.level().isClientSide) {
                    ItemStack old = this.getItemBySlot(slot);
                    if (!old.isEmpty()) {
                        this.spawnAtLocation(old); // старый предмет роняем под ноги, а не удаляем молча
                    }
                    this.setItemSlot(slot, itemstack.copyWithCount(1));
                    if (!player.getAbilities().instabuild) {
                        itemstack.shrink(1);
                    }
                    this.playSound(net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_GENERIC.value(), 1.0F, 1.0F);
                }
                return InteractionResult.sidedSuccess(this.level().isClientSide);
            }

            // Наездник — ПКМ Седлом оседлывает свинью (один раз); дальше ПКМ пустой рукой садит
            // хозяина верхом вместо обычного сесть/встать или поедания морковки (см. заметку в
            // Mutations.RIDER о том, почему эти действия не могут сосуществовать на одном клике).
            if (this.hasMutation("rider")) {
                if (!this.isSaddled() && itemstack.is(Items.SADDLE) && !this.isBaby()) {
                    if (!this.level().isClientSide) {
                        if (!player.getAbilities().instabuild) {
                            itemstack.shrink(1);
                        }
                        this.setSaddled(true);
                        this.playSound(net.minecraft.sounds.SoundEvents.HORSE_SADDLE, 1.0F, 1.0F);
                    }
                    return InteractionResult.sidedSuccess(this.level().isClientSide);
                }
                if (this.isSaddled() && itemstack.isEmpty() && hand == InteractionHand.MAIN_HAND
                        && !this.isVehicle() && !player.isShiftKeyDown()) {
                    if (!this.level().isClientSide) {
                        player.startRiding(this);
                    }
                    return InteractionResult.sidedSuccess(this.level().isClientSide);
                }
            }

            // Если игрок зажал Shift + ПКМ пустой рукой — показываем инфо в чат
            // Если игрок зажал Shift + ПКМ — открываем GUI Свиньи!
    if (player.isShiftKeyDown() && hand == InteractionHand.MAIN_HAND) {
        if (!this.level().isClientSide) {
            player.openMenu(new SimpleMenuProvider(
                (containerId, playerInv, p) -> new PigMenu(containerId, playerInv, this),
                Component.translatable("container.pig_mod.pig_inventory")
            ), buf -> buf.writeInt(this.getId()));
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

            // Моркосвинья — ПКМ пустой рукой "съедает" морковку с хвоста (если мутация есть и не на
            // кулдауне). Если кулдаун ещё не прошёл — просто проваливаемся дальше, к обычному "сесть/встать".
            if (hand == InteractionHand.MAIN_HAND && itemstack.isEmpty() && this.tryEatCarrot(player)) {
                return InteractionResult.sidedSuccess(this.level().isClientSide);
            }

            // Обычный ПКМ больше НЕ переключает Сидеть/Следовать. Команды меняются только через GUI.
            // Это специально убирает конфликт с будущими командами и с обычными взаимодействиями.
            if (hand == InteractionHand.MAIN_HAND && itemstack.isEmpty()) {
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
        if (this.getCourierChestPos() != null) {
            compound.putLong("CourierChestPos", this.getCourierChestPos().asLong());
        }
        compound.putInt("SizeState", this.getSizeState());
        compound.putBoolean("Saddled", this.isSaddled());
        compound.putInt("AngelCooldown", this.angelCooldown);
        compound.putInt("ExplosiveCooldown", this.explosiveCooldown);
        compound.putInt("DragonFireballCooldown", this.dragonFireballCooldown);
        compound.putInt("PsychicCooldown", this.psychicCooldown);
        compound.putBoolean("RaidMode", this.isRaidMode());
        compound.putInt("Gender", this.entityData.get(DATA_GENDER));
        compound.putInt("Command", getCommand().getId());
        compound.putInt("Hunger", getHunger());
        if (this.getWorkAreaPoint1() != null) compound.putLong("WorkAreaPoint1", this.getWorkAreaPoint1().asLong());
        if (this.getWorkAreaPoint2() != null) compound.putLong("WorkAreaPoint2", this.getWorkAreaPoint2().asLong());
        net.minecraft.nbt.ListTag whitelistTag = new net.minecraft.nbt.ListTag();
        for (ResourceLocation id : this.getWhitelistIds()) whitelistTag.add(net.minecraft.nbt.StringTag.valueOf(id.toString()));
        compound.put("WhitelistEntityTypes", whitelistTag);

        // Сохраняем инвентарь свиньи отдельно от её прочих данных.
        // В Minecraft 1.21.1 ItemStack сериализуется через registry access сущности.
        net.minecraft.nbt.ListTag inventoryTag = new net.minecraft.nbt.ListTag();
        for (int i = 0; i < this.inventory.getContainerSize(); i++) {
            ItemStack stack = this.inventory.getItem(i);
            if (!stack.isEmpty()) {
                CompoundTag itemTag = new CompoundTag();
                itemTag.putInt("Slot", i);
                stack.save(this.registryAccess(), itemTag);
                inventoryTag.add(itemTag);
            }
        }
        compound.put("PigInventory", inventoryTag);
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
        this.updateFlightMovementMode();
        if (compound.contains("CarrotCooldown")) {
            this.carrotCooldown = compound.getInt("CarrotCooldown");
        }
        if (compound.contains("CourierChestPos")) {
            this.setCourierChestPos(BlockPos.of(compound.getLong("CourierChestPos")));
        }
        if (compound.contains("SizeState")) {
            this.setSizeState(compound.getInt("SizeState")); // применит бонусы атрибутов заново
        }
        if (compound.contains("Saddled")) {
            this.setSaddled(compound.getBoolean("Saddled"));
        }
        if (compound.contains("RaidMode")) {
            this.setRaidMode(compound.getBoolean("RaidMode"));
        }
        if (compound.contains("Gender")) {
            int gender = compound.getInt("Gender");
            this.entityData.set(DATA_GENDER, gender == 1 ? 1 : 0);
        } else {
            this.entityData.set(DATA_GENDER, -1);
        }
        if (compound.contains("Command")) {
            setCommand(PigCommand.fromId(compound.getInt("Command")));
        } else {
            // Совместимость со старыми сохранениями: состояние "сидеть" переносим в новую команду.
            setCommand(this.isOrderedToSit() ? PigCommand.SIT : PigCommand.FOLLOW);
        }
        if (compound.contains("Hunger")) {
            setHunger(compound.getInt("Hunger"));
        } else {
            this.entityData.set(DATA_HUNGER, getMaxHunger());
        }
        if (compound.contains("WorkAreaPoint1")) this.setWorkAreaPoint1(BlockPos.of(compound.getLong("WorkAreaPoint1")));
        if (compound.contains("WorkAreaPoint2")) this.setWorkAreaPoint2(BlockPos.of(compound.getLong("WorkAreaPoint2")));
        if (compound.contains("WhitelistEntityTypes")) {
            net.minecraft.nbt.ListTag list=compound.getList("WhitelistEntityTypes", net.minecraft.nbt.Tag.TAG_STRING);
            java.util.Set<ResourceLocation> loaded = new java.util.LinkedHashSet<>();
            for(int i=0;i<list.size();i++){ try { loaded.add(ResourceLocation.parse(list.getString(i))); } catch(Exception ignored){} }
            writeWhitelist(loaded);
        }

        // В старых сохранениях PigInventory отсутствует — тогда остаются пустые слоты.
        if (compound.contains("PigInventory")) {
            net.minecraft.nbt.ListTag inventoryTag = compound.getList("PigInventory", net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int i = 0; i < inventoryTag.size(); i++) {
                CompoundTag itemTag = inventoryTag.getCompound(i);
                int slot = itemTag.getInt("Slot");
                if (slot >= 0 && slot < this.inventory.getContainerSize()) {
                    this.inventory.setItem(slot, ItemStack.parseOptional(this.registryAccess(), itemTag));
                }
            }
        }

        this.angelCooldown = Math.max(0, compound.getInt("AngelCooldown"));
        this.explosiveCooldown = Math.max(0, compound.getInt("ExplosiveCooldown"));
        this.dragonFireballCooldown = Math.max(0, compound.getInt("DragonFireballCooldown"));
        this.psychicCooldown = Math.max(0, compound.getInt("PsychicCooldown"));
        this.refreshDragonState();
    }

    @Override
    public boolean canMate(net.minecraft.world.entity.animal.Animal otherAnimal) {
        if (!(otherAnimal instanceof TameablePigEntity otherPig)) {
            return false;
        }
        if (!this.isTame() || !otherPig.isTame() || this.isBaby() || otherPig.isBaby()) {
            return false;
        }
        // Главный гендерный запрет: мужской + мужской и женский + женский никогда не дают ребёнка.
        if (this.isFemale() == otherPig.isFemale()) {
            return false;
        }
        return super.canMate(otherAnimal);
    }

    @Override
    public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob otherParent) {
        TameablePigEntity otherPig = otherParent instanceof TameablePigEntity pig ? pig : null;
        TameablePigEntity child = ModEntities.TAMEABLE_PIG.get().create(level);
        if (child == null) {
            return null;
        }

        child.setBaby(true);
        child.setFemale(level.random.nextBoolean());
        child.setHunger(child.getMaxHunger());

        // Если родители принадлежат одному хозяину, ребёнок сразу становится его свиньёй.
        if (this.isTame() && otherPig != null && otherPig.isTame()
                && this.getOwnerUUID() != null && this.getOwnerUUID().equals(otherPig.getOwnerUUID())
                && this.getOwner() instanceof Player owner) {
            child.tame(owner);
            child.setCommand(PigCommand.FOLLOW);
        }
        return child;
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