package cn.allay.server.client;

import cn.allay.api.annotation.SlowOperation;
import cn.allay.api.block.data.BlockFace;
import cn.allay.api.block.interfaces.BlockAirBehavior;
import cn.allay.api.block.type.BlockTypeRegistry;
import cn.allay.api.client.BaseClient;
import cn.allay.api.client.data.AdventureSettings;
import cn.allay.api.client.data.LoginData;
import cn.allay.api.container.Container;
import cn.allay.api.container.FixedContainerId;
import cn.allay.api.container.FullContainerType;
import cn.allay.api.container.SimpleContainerActionProcessorHolder;
import cn.allay.api.container.processor.ContainerActionProcessor;
import cn.allay.api.container.processor.ContainerActionProcessorHolder;
import cn.allay.api.entity.Entity;
import cn.allay.api.entity.attribute.Attribute;
import cn.allay.api.entity.init.SimpleEntityInitInfo;
import cn.allay.api.entity.interfaces.player.EntityPlayer;
import cn.allay.api.entity.interfaces.player.SimpleEntityPlayerInitInfo;
import cn.allay.api.entity.interfaces.villagerv2.EntityVillagerV2;
import cn.allay.api.entity.type.EntityTypeRegistry;
import cn.allay.api.item.ItemStack;
import cn.allay.api.item.type.CreativeItemRegistry;
import cn.allay.api.item.type.ItemTypeRegistry;
import cn.allay.api.math.location.Location3f;
import cn.allay.api.math.position.Position3ic;
import cn.allay.api.server.Server;
import cn.allay.api.utils.MathUtils;
import cn.allay.api.world.biome.BiomeTypeRegistry;
import cn.allay.api.world.chunk.Chunk;
import cn.allay.api.world.gamerule.GameRule;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.cloudburstmc.math.vector.Vector2f;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.BedrockServerSession;
import org.cloudburstmc.protocol.bedrock.data.*;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.ItemStackRequestAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.ItemStackRequestActionType;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.response.ItemStackResponse;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventorySource;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryTransactionType;
import org.cloudburstmc.protocol.bedrock.packet.*;
import org.cloudburstmc.protocol.bedrock.util.EncryptionUtils;
import org.cloudburstmc.protocol.common.PacketSignal;
import org.cloudburstmc.protocol.common.SimpleDefinitionRegistry;
import org.cloudburstmc.protocol.common.util.OptionalBoolean;
import org.joml.Vector3fc;
import org.joml.Vector3ic;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Allay Project 2023/6/23
 *
 * @author daoge_cmd
 */
@Slf4j
public class AllayClient extends BaseClient {
    private final BedrockServerSession session;
    private final AtomicBoolean loggedIn = new AtomicBoolean(false);
    private final AtomicBoolean online = new AtomicBoolean(false);
    private final AtomicBoolean firstSpawned = new AtomicBoolean(false);
    private final AtomicBoolean localInitialized = new AtomicBoolean(false);
    private final AtomicInteger doFirstSpawnChunkThreshold;
    @Getter
    private final ContainerActionProcessorHolder containerActionProcessorHolder;
    @Setter
    private Function<SubChunkRequestPacket, SubChunkPacket> subChunkRequestHandler =
            packet -> {
                throw new UnsupportedOperationException();
            };

    private AllayClient(BedrockServerSession session, Server server) {
        this.session = session;
        this.server = server;
        this.chunkLoadingRadius = server.getServerSettings().worldSettings().viewDistance();
        this.chunkTrySendCountPerTick = server.getServerSettings().worldSettings().chunkTrySendCountPerTick();
        this.adventureSettings = new AdventureSettings(this);
        session.setPacketHandler(new AllayClientPacketHandler());
        containerActionProcessorHolder = new SimpleContainerActionProcessorHolder();
        ContainerActionProcessorHolder.registerDefaultContainerActionProcessors(containerActionProcessorHolder);
        doFirstSpawnChunkThreshold = new AtomicInteger(server.getServerSettings().worldSettings().doFirstSpawnChunkThreshold());
    }

    public static AllayClient hold(BedrockServerSession session, Server Server) {
        return new AllayClient(session, Server);
    }

    @Override
    public boolean isLocalInitialized() {
        return localInitialized.get();
    }

    @Override
    public void setChunkLoadingRadius(int radius) {
        chunkLoadingRadius = Math.min(radius, server.getServerSettings().worldSettings().viewDistance());
        var chunkRadiusUpdatedPacket = new ChunkRadiusUpdatedPacket();
        chunkRadiusUpdatedPacket.setRadius(chunkLoadingRadius);
        sendPacket(chunkRadiusUpdatedPacket);
    }

    @Override
    public void preSendChunks(Set<Long> chunkHashes) {
        var chunkPublisherUpdatePacket = new NetworkChunkPublisherUpdatePacket();
        var loc = getLocation();
        chunkPublisherUpdatePacket.setPosition(Vector3i.from(loc.x(), loc.y(), loc.z()));
        chunkPublisherUpdatePacket.setRadius(getChunkLoadingRadius() << 4);
        sendPacket(chunkPublisherUpdatePacket);
    }

    private void doFirstSpawn() {
        if (firstSpawned.get()) {
            return;
        }
        server.getClientStorage().readClientData(this);

        var setEntityDataPacket = new SetEntityDataPacket();
        setEntityDataPacket.setRuntimeEntityId(playerEntity.getUniqueId());
        setEntityDataPacket.getMetadata().putAll(playerEntity.getMetadata().getEntityDataMap());
        setEntityDataPacket.setTick(server.getTicks());
        sendPacket(setEntityDataPacket);

        adventureSettings.set(AdventureSettings.Type.OPERATOR, isOp());
        adventureSettings.set(AdventureSettings.Type.TELEPORT, true);
        adventureSettings.set(AdventureSettings.Type.WORLD_IMMUTABLE, gameType == GameType.SPECTATOR);
        adventureSettings.set(AdventureSettings.Type.ALLOW_FLIGHT, gameType != GameType.SURVIVAL && gameType != GameType.ADVENTURE);
        adventureSettings.set(AdventureSettings.Type.NO_CLIP, gameType == GameType.SPECTATOR);
        adventureSettings.set(AdventureSettings.Type.FLYING, gameType == GameType.SPECTATOR);
        adventureSettings.set(AdventureSettings.Type.ATTACK_MOBS, gameType == GameType.SURVIVAL || gameType == GameType.CREATIVE);
        adventureSettings.set(AdventureSettings.Type.ATTACK_PLAYERS, gameType == GameType.SURVIVAL || gameType == GameType.CREATIVE);
        adventureSettings.set(AdventureSettings.Type.NO_PVM, gameType == GameType.SPECTATOR);
        adventureSettings.update();

        //TODO: CommandData

        var updateAttributesPacket = new UpdateAttributesPacket();
        updateAttributesPacket.setRuntimeEntityId(playerEntity.getUniqueId());
        for (Attribute attribute : playerEntity.getAttributes()) {
            updateAttributesPacket.getAttributes().add(attribute.toNetwork());
        }
        updateAttributesPacket.setTick(server.getTicks());
        sendPacket(updateAttributesPacket);

        server.addToPlayerList(this);
        if (server.getOnlineClientCount() > 1) {
            server.sendFullPlayerListInfoTo(this);
        }

        sendInventories();

        firstSpawned.set(true);
        var playStatusPacket = new PlayStatusPacket();
        playStatusPacket.setStatus(PlayStatusPacket.Status.PLAYER_SPAWN);
        sendPacket(playStatusPacket);

        //TODO: SetTime
    }

    private void sendInventories() {
        //TODO: setHolder
        playerEntity.sendContentsWithSpecificContainerId(playerEntity.getContainer(FullContainerType.PLAYER_INVENTORY), FixedContainerId.PLAYER_INVENTORY);
        playerEntity.sendContentsWithSpecificContainerId(playerEntity.getContainer(FullContainerType.OFFHAND), FixedContainerId.OFFHAND);
        playerEntity.sendContentsWithSpecificContainerId(playerEntity.getContainer(FullContainerType.ARMOR), FixedContainerId.ARMOR);
        //No need to send cursor's content to client because there is nothing in cursor
    }

    @Override
    public boolean isLoggedIn() {
        return loggedIn.get();
    }

    @Override
    public boolean isFirstSpawned() {
        return firstSpawned.get();
    }

    @Override
    public void sendPacket(BedrockPacket packet) {
        session.sendPacket(packet);
    }

    @Override
    public void sendPacketImmediately(BedrockPacket packet) {
        session.sendPacketImmediately(packet);
    }

    @Override
    public void disconnect(String reason) {
        disconnect(reason, false);
    }

    @Override
    public void disconnect(String reason, boolean hideReason) {
        session.disconnect(reason, hideReason);
    }

    /**
     * 登入完成后调用。发送玩家需要的数据并生成玩家实体。此时可以认为玩家已进服但未加载完成
     */
    @Override
    public void initializePlayer() {
        initPlayerEntity();
        sendBasicGameData();
        getWorld().getChunkService().getOrLoadChunk(
                (int) playerEntity.getLocation().x() >> 4,
                (int) playerEntity.getLocation().z() >> 4
        ).thenAcceptAsync((c) -> {
            getWorld().addClient(this);
            online.set(true);
        }, Server.getInstance().getVirtualThreadPool()).exceptionally(
                t -> {
                    log.error("Error while initialize player " + getName() + "!", t);
                    return null;
                }
        );
    }

    @Override
    public boolean computeMovementServerSide() {
        return false;
    }

    private void initPlayerEntity() {
        Position3ic spawnPos = server.getDefaultWorld().getSpawnPosition();
        playerEntity = EntityPlayer.PLAYER_TYPE.createEntity(
                SimpleEntityPlayerInitInfo
                        .builder()
                        .client(this)
                        .pos(spawnPos.x(), spawnPos.y(), spawnPos.z())
                        .world(spawnPos.world())
                        .build()
        );
    }

    private void sendBasicGameData() {
        var spawnWorld = server.getDefaultWorld();
        var startGamePacket = new StartGamePacket();
        startGamePacket.getGamerules().add(GameRule.SHOW_COORDINATES.toNetwork());
        startGamePacket.setUniqueEntityId(playerEntity.getUniqueId());
        startGamePacket.setRuntimeEntityId(playerEntity.getUniqueId());
        startGamePacket.setPlayerGameType(gameType);
        var loc = playerEntity.getLocation();
        var worldSpawn = spawnWorld.getSpawnPosition();
        startGamePacket.setDefaultSpawn(Vector3i.from(worldSpawn.x(), worldSpawn.y(), worldSpawn.z()));
        startGamePacket.setPlayerPosition(Vector3f.from(loc.x(), loc.y(), loc.z()));
        startGamePacket.setRotation(Vector2f.from(loc.pitch(), loc.yaw()));
        startGamePacket.setSeed(spawnWorld.getWorldData().getRandomSeed());
        startGamePacket.setDimensionId(spawnWorld.getDimensionInfo().dimensionId());
        startGamePacket.setGeneratorId(spawnWorld.getWorldGenerator().getType().getId());
        startGamePacket.setLevelGameType(spawnWorld.getWorldGameType());
        startGamePacket.setDifficulty(spawnWorld.getDifficulty().ordinal());
        startGamePacket.setTrustingPlayers(true);
        startGamePacket.setDayCycleStopTime(0);
        startGamePacket.setLevelName(server.getServerSettings().genericSettings().motd());
        //TODO
        startGamePacket.setLevelId("");
        //TODO
        startGamePacket.setDefaultPlayerPermission(server.getServerSettings().genericSettings().defaultPermission());
        startGamePacket.setServerChunkTickRange(spawnWorld.getTickingRadius());
        startGamePacket.setVanillaVersion(server.getNetworkServer().getCodec().getMinecraftVersion());
        startGamePacket.setPremiumWorldTemplateId("");
        startGamePacket.setInventoriesServerAuthoritative(true);
        startGamePacket.setItemDefinitions(ItemTypeRegistry.getRegistry().getItemDefinitions());
        startGamePacket.setAuthoritativeMovementMode(AuthoritativeMovementMode.SERVER);
        startGamePacket.setServerAuthoritativeBlockBreaking(true);
        startGamePacket.setCommandsEnabled(true);
        startGamePacket.setMultiplayerGame(true);
        startGamePacket.setBroadcastingToLan(true);
        startGamePacket.setMultiplayerCorrelationId(UUID.randomUUID().toString());
        startGamePacket.setXblBroadcastMode(GamePublishSetting.PUBLIC);
        startGamePacket.setPlatformBroadcastMode(GamePublishSetting.PUBLIC);
        //TODO
        startGamePacket.setCurrentTick(0);
        startGamePacket.setServerEngine("Allay");
        startGamePacket.setBlockRegistryChecksum(0L);
        startGamePacket.setPlayerPropertyData(NbtMap.EMPTY);
        startGamePacket.setWorldTemplateId(new UUID(0, 0));
        startGamePacket.setWorldEditor(false);
        startGamePacket.setChatRestrictionLevel(ChatRestrictionLevel.NONE);
        startGamePacket.setSpawnBiomeType(SpawnBiomeType.DEFAULT);
        startGamePacket.setCustomBiomeName("");
        startGamePacket.setEducationProductionId("");
        startGamePacket.setForceExperimentalGameplay(OptionalBoolean.empty());
        startGamePacket.setBlockNetworkIdsHashed(true);
        sendPacket(startGamePacket);

        session.getPeer().getCodecHelper().setItemDefinitions(
                SimpleDefinitionRegistry
                        .<ItemDefinition>builder()
                        .addAll(startGamePacket.getItemDefinitions())
                        .build()
        );

        session.getPeer().getCodecHelper().setBlockDefinitions(
                SimpleDefinitionRegistry
                        .<BlockDefinition>builder()
                        .addAll(BlockTypeRegistry.getRegistry().getBlockDefinitions())
                        .build()
        );

        var availableEntityIdentifiersPacket = new AvailableEntityIdentifiersPacket();
        availableEntityIdentifiersPacket.setIdentifiers(EntityTypeRegistry.getRegistry().getAvailableEntityIdentifierTag());
        sendPacket(availableEntityIdentifiersPacket);

        var biomeDefinitionListPacket = new BiomeDefinitionListPacket();
        biomeDefinitionListPacket.setDefinitions(BiomeTypeRegistry.getRegistry().getBiomeDefinition());
        sendPacket(biomeDefinitionListPacket);

        var creativeContentPacket = new CreativeContentPacket();
        creativeContentPacket.setContents(CreativeItemRegistry.getRegistry().getNetworkItemDataArray());
        sendPacket(creativeContentPacket);

        var craftingDataPacket = new CraftingDataPacket();
        craftingDataPacket.setCleanRecipes(true);
        sendPacket(craftingDataPacket);
    }

    @Override
    public boolean isLoaderActive() {
        return isOnline();
    }

    @Override
    public boolean isOnline() {
        return online.get();
    }

    @SlowOperation
    @Override
    public void onChunkInRangeLoaded(Chunk chunk) {
        var levelChunkPacket = chunk.createLevelChunkPacket();
        sendPacket(levelChunkPacket);
        chunk.spawnEntitiesTo(this);
        if (doFirstSpawnChunkThreshold.get() > 0) {
            if (doFirstSpawnChunkThreshold.decrementAndGet() == 0) {
                doFirstSpawn();
            }
        }
    }

    @Override
    public void onChunkOutOfRange(Set<Long> chunkHashes) {
        chunkHashes
                .stream()
                .map(getWorld().getChunkService()::getChunk)
                .forEach(chunk -> chunk.despawnEntitiesFrom(this));
    }

    @Override
    public void spawnEntity(Entity entity) {
        entity.spawnTo(this);
    }

    @Override
    public void despawnEntity(Entity entity) {
        entity.despawnFrom(this);
    }

    private class AllayClientPacketHandler implements BedrockPacketHandler {

        public static final Pattern NAME_PATTERN = Pattern.compile("^(?! )([a-zA-Z0-9_ ]{2,15}[a-zA-Z0-9_])(?<! )$");

        @Override
        public void onDisconnect(String reason) {
            if (firstSpawned.get()) server.getClientStorage().writeClientData(AllayClient.this);
            server.onDisconnect(AllayClient.this);
            if (playerEntity != null)
                playerEntity.getLocation().world().removeClient(AllayClient.this);
        }

        @Override
        public PacketSignal handle(RequestNetworkSettingsPacket packet) {
            var protocolVersion = packet.getProtocolVersion();
            var supportedProtocolVersion = server.getNetworkServer().getCodec().getProtocolVersion();
            if (protocolVersion != supportedProtocolVersion) {
                var loginFailedPacket = new PlayStatusPacket();
                if (protocolVersion > supportedProtocolVersion) {
                    loginFailedPacket.setStatus(PlayStatusPacket.Status.LOGIN_FAILED_SERVER_OLD);
                } else {
                    loginFailedPacket.setStatus(PlayStatusPacket.Status.LOGIN_FAILED_CLIENT_OLD);
                }
                session.sendPacketImmediately(loginFailedPacket);
                return PacketSignal.HANDLED;
            }
            var settingsPacket = new NetworkSettingsPacket();
            settingsPacket.setCompressionAlgorithm(server.getServerSettings().networkSettings().compressionAlgorithm());
            settingsPacket.setCompressionThreshold(server.getServerSettings().networkSettings().compressionThreshold());
            sendPacketImmediately(settingsPacket);
            session.setCompression(settingsPacket.getCompressionAlgorithm());
            session.setCompressionLevel(settingsPacket.getCompressionThreshold());
            return PacketSignal.HANDLED;
        }

        @Override
        public PacketSignal handle(LoginPacket packet) {
            loginData = LoginData.decode(packet);

            if (!loginData.isXboxAuthenticated() && server.getServerSettings().networkSettings().xboxAuth()) {
                disconnect("disconnectionScreen.notAuthenticated");
                return PacketSignal.HANDLED;
            }

            name = loginData.getDisplayName();
            displayName = loginData.getDisplayName();
            if (!NAME_PATTERN.matcher(name).matches()) {
                disconnect("disconnectionScreen.invalidName");
                return PacketSignal.HANDLED;
            }

            if (server.getOnlineClients().containsKey(name)) {
                disconnect("disconnectionScreen.loggedinOtherLocation");
                return PacketSignal.HANDLED;
            }

            if (!loginData.getSkin().isValid()) {
                session.disconnect("disconnectionScreen.invalidSkin");
                return PacketSignal.HANDLED;
            }

            if (server.getServerSettings().networkSettings().enableNetworkEncryption()) {
                try {
                    var clientKey = EncryptionUtils.parseKey(loginData.getIdentityPublicKey());
                    var encryptionKeyPair = EncryptionUtils.createKeyPair();
                    var encryptionToken = EncryptionUtils.generateRandomToken();
                    encryptionSecretKey = EncryptionUtils.getSecretKey(
                            encryptionKeyPair.getPrivate(), clientKey,
                            encryptionToken
                    );
                    var encryptionJWT = EncryptionUtils.createHandshakeJwt(encryptionKeyPair, encryptionToken);
                    networkEncryptionEnabled = true;
                    var handshakePacket = new ServerToClientHandshakePacket();
                    handshakePacket.setJwt(encryptionJWT);
                    sendPacketImmediately(handshakePacket);
                    session.enableEncryption(encryptionSecretKey);
                    //completeLogin() when client send back ClientToServerHandshakePacket
                } catch (Exception exception) {
                    log.warn("Failed to initialize encryption for client " + name, exception);
                    disconnect("disconnectionScreen.internalError");
                }
            } else {
                completeLogin();
            }

            return PacketSignal.HANDLED;
        }

        @Override
        public PacketSignal handle(ClientToServerHandshakePacket packet) {
            if (isNetworkEncryptionEnabled()) {
                completeLogin();
            } else log.warn("Client " + name + " sent ClientToServerHandshakePacket without encryption enabled");
            return PacketSignal.HANDLED;
        }

        protected void completeLogin() {
            var playStatusPacket = new PlayStatusPacket();
            if (server.getOnlineClientCount() >= server.getServerSettings().genericSettings().maxClientCount()) {
                playStatusPacket.setStatus(PlayStatusPacket.Status.FAILED_SERVER_FULL_SUB_CLIENT);
            } else {
                playStatusPacket.setStatus(PlayStatusPacket.Status.LOGIN_SUCCESS);
            }
            sendPacket(playStatusPacket);
            server.onLoggedIn(AllayClient.this);
            loggedIn.set(true);
            //TODO: Resource Packs
            sendPacket(new ResourcePacksInfoPacket());
        }

        @Override
        public PacketSignal handle(ResourcePackClientResponsePacket packet) {
            switch (packet.getStatus()) {
                case SEND_PACKS -> {
                    //TODO: RP
                }
                case HAVE_ALL_PACKS -> {
                    var stackPacket = new ResourcePackStackPacket();
                    stackPacket.setGameVersion(server.getNetworkServer().getCodec().getMinecraftVersion());
                    sendPacket(stackPacket);
                }
                case COMPLETED -> {
                    initializePlayer();
                }
            }
            return PacketSignal.HANDLED;
        }

        @Override
        public PacketSignal handle(RequestChunkRadiusPacket packet) {
            setChunkLoadingRadius(packet.getRadius());
            return PacketSignal.HANDLED;
        }

        @Override
        public PacketSignal handle(SetLocalPlayerAsInitializedPacket packet) {
            localInitialized.set(true);
            return PacketSignal.HANDLED;
        }

        @Override
        public PacketSignal handle(InteractPacket packet) {
            switch (packet.getAction()) {
                case OPEN_INVENTORY -> {
                    playerEntity.getContainer(FullContainerType.PLAYER_INVENTORY).addViewer(playerEntity);
                }
            }
            return PacketSignal.HANDLED;
        }

        @Override
        public PacketSignal handle(ContainerClosePacket packet) {
            var opened = playerEntity.getOpenedContainer(packet.getId());
            if (opened == null)
                throw new IllegalStateException("Player is not viewing an inventory");
            opened.removeViewer(playerEntity);
            return PacketSignal.HANDLED;
        }

        @Override
        public PacketSignal handle(ItemStackRequestPacket packet) {
            List<ItemStackResponse> responses = new LinkedList<>();
            for (var request : packet.getRequests()) {
                var chainInfo = new LinkedHashMap<ItemStackRequestActionType, ItemStackResponse>();
                //chain process
                for (var action : request.getActions()) {
                    ContainerActionProcessor<ItemStackRequestAction> processor = containerActionProcessorHolder.getProcessor(action.getType());
                    if (processor == null) {
                        log.warn("Unhandled inventory action type " + action.getType());
                        continue;
                    }
                    chainInfo.put(action.getType(), processor.handle(action, AllayClient.this, request.getRequestId(), chainInfo));
                }
                //add process result
                for (var res : chainInfo.values()) {
                    if (res != null) {
                        responses.add(res);
                    }
                }
            }
            var itemStackResponsePacket = new ItemStackResponsePacket();
            itemStackResponsePacket.getEntries().addAll(responses);
            sendPacket(itemStackResponsePacket);
            return PacketSignal.HANDLED;
        }

        @Override
        public PacketSignal handle(SubChunkRequestPacket packet) {
            sendPacket(subChunkRequestHandler.apply(packet));
            return PacketSignal.HANDLED;
        }

        @Override
        public PacketSignal handle(MobEquipmentPacket packet) {
            var handSlot = packet.getHotbarSlot();
            playerEntity.setHandSlot(handSlot);
            return PacketSignal.HANDLED;
        }

        protected long spamCheckTime;

        @Override
        public PacketSignal handle(InventoryTransactionPacket packet) {
            if (packet.getTransactionType() == InventoryTransactionType.ITEM_USE) {
                Vector3ic blockPos = MathUtils.cbVecToJOMLVec(packet.getBlockPosition());
                Vector3fc clickPos = MathUtils.cbVecToJOMLVec(packet.getClickPosition());
                BlockFace blockFace = BlockFace.fromId(packet.getBlockFace());
                var inv = playerEntity.getContainer(FullContainerType.PLAYER_INVENTORY);
                var itemStack = inv.getItemInHand();
                switch (packet.getActionType()) {
                    case 0 -> {
                        var placePos = blockFace.offsetPos(blockPos);
                        if (!canInteract()) {
                            //TODO: 确认是否需要发送UpdateBlockPacket
                            var blockState = getWorld().getBlockState(placePos.x(), placePos.y(), placePos.z());
                            getWorld().sendBlockUpdateTo(blockState, placePos.x(), placePos.y(), placePos.z(), 0, AllayClient.this);
                            return PacketSignal.HANDLED;
                        }
                        this.spamCheckTime = System.currentTimeMillis();

                        if (!useItemOn(itemStack, blockPos, placePos, clickPos, blockFace)) {
                            //Failed to use the item, send back origin block state to client
                            var w = getWorld();
                            var blockStateClicked = w.getBlockState(blockPos.x(), blockPos.y(), blockPos.z());
                            w.sendBlockUpdateTo(blockStateClicked, blockPos.x(), blockPos.y(), blockPos.z(), 0, AllayClient.this);

                            var blockStateReplaced = w.getBlockState(placePos.x(), placePos.y(), placePos.z());
                            w.sendBlockUpdateTo(blockStateReplaced, placePos.x(), placePos.y(), placePos.z(), 0, AllayClient.this);
                        } else {
                            //Used! Update item slot to client
                            if (itemStack.getCount() != 0) {
                                inv.onSlotChange(inv.getHandSlot());
                            } else {
                                inv.setItemInHand(Container.EMPTY_SLOT_PLACE_HOLDER);
                            }
                        }
                    }
                }
            } else if (packet.getTransactionType() == InventoryTransactionType.NORMAL) {
                for (var action : packet.getActions()) {
                    if (action.getSource().getType().equals(InventorySource.Type.WORLD_INTERACTION)) {
                        if (action.getSource().getFlag().equals(InventorySource.Flag.DROP_ITEM)) {
                            //Do not ask me why mojang still use the old item transaction packet even the server-auth inv was enabled
                            var count = action.getToItem().getCount();
                            playerEntity.tryDropItemInHand(count);
                        }
                    }
                }
            }
            return PacketSignal.HANDLED;
        }

        private boolean useItemOn(ItemStack itemStack, Vector3ic blockPos, Vector3ic placePos, Vector3fc clickPos, BlockFace blockFace) {
            var world = getWorld();
            var blockStateClicked = world.getBlockState(blockPos.x(), blockPos.y(), blockPos.z());
            if (!blockStateClicked.getBehavior().onInteract(playerEntity, itemStack, world, blockPos, placePos, clickPos, blockFace))
                return itemStack.useItemOn(playerEntity, itemStack, getWorld(), blockPos, placePos, clickPos, blockFace);
            else return true;
        }

        protected boolean canInteract() {
            return System.currentTimeMillis() - this.spamCheckTime >= 100;
        }

        @Override
        public PacketSignal handle(PlayerAuthInputPacket packet) {
            if (!isOnline()) return PacketSignal.UNHANDLED;
            //客户端发送给服务端的坐标比实际坐标高了一个BaseOffset，我们需要减掉它
            handleMovement(packet.getPosition().sub(0, playerEntity.getBaseOffset(), 0), packet.getRotation());
            handleBlockAction(packet.getPlayerActions());
            handleInputData(packet.getInputData());
            return PacketSignal.HANDLED;
        }

        @Override
        public PacketSignal handle(MovePlayerPacket packet) {
            //In server-auth movement, the MovePlayerPacket is only used to send "onGround" state to server by client
            if (!isOnline()) return PacketSignal.UNHANDLED;
            if (!packet.isOnGround()) {
                log.warn("Player " + name + " send a invalid MovePlayerPacket (onGround=false) while using server-auth movement!");
                return PacketSignal.HANDLED;
            }
            if (!movementValidator.validateOnGround()) {
                log.warn("Player " + name + " thinks he landed but didn't in fact!");
                return PacketSignal.HANDLED;
            }
            playerEntity.setOnGround(true);
            return PacketSignal.HANDLED;
        }

        @Override
        public PacketSignal handle(AnimatePacket packet) {
            if (packet.getAction() == AnimatePacket.Action.SWING_ARM) {
                getPlayerEntity().getCurrentChunk().addChunkPacket(packet);
            }
            return PacketSignal.HANDLED;
        }

        @Override
        public PacketSignal handle(TextPacket packet) {
            if (packet.getType() == TextPacket.Type.CHAT) {
                server.broadcastChat(AllayClient.this, packet.getMessage());
                //TODO: debug only
                if (packet.getMessage().equals("spawn v")) {
                    var loc = getLocation();
                    for (var i = 0; i <= 0; i++) {
                        var entity = EntityVillagerV2.VILLAGER_V2_TYPE.createEntity(
                                SimpleEntityInitInfo
                                        .builder()
                                        .pos(loc.x() + i, loc.y(), loc.z() + i)
                                        .world(loc.world())
                                        .build()
                        );
                        loc.world().addEntity(entity);
                    }
                    sendRawMessage("TPS: " + loc.world().getTps() + ", Entity Count: " + loc.world().getEntities().size());
                }
            }
            return PacketSignal.HANDLED;
        }

        protected void handleMovement(Vector3f newPos, Vector3f newRot) {
            var valid = movementValidator.validate(new Location3f(
                    newPos.getX(), newPos.getY(), newPos.getZ(),
                    newRot.getX(), newRot.getY(), newRot.getZ(),
                    getWorld())
            );
            if (!valid) {
                log.warn("Player " + name + " tried to move to invalid location");
                return;
            }
            getWorld().getEntityPhysicsService()
                    .offerScheduledMove(
                            playerEntity,
                            new Location3f(
                                    newPos.getX(), newPos.getY(), newPos.getZ(),
                                    newRot.getX(), newRot.getY(), newRot.getZ(),
                                    getWorld())
                    );
        }

        protected void handleBlockAction(List<PlayerBlockActionData> blockActions) {
            if (blockActions.isEmpty()) return;
            for (var action : blockActions) {
                var pos = action.getBlockPosition();
                //TODO: checking
                switch (action.getAction()) {
                    case START_BREAK -> {
                        getWorld().sendLevelEventPacket(pos, LevelEvent.BLOCK_START_BREAK, 0);
                    }
                    case BLOCK_PREDICT_DESTROY -> {
                        var oldState = getWorld().getBlockState(pos.getX(), pos.getY(), pos.getZ());
                        getWorld().setBlockState(pos.getX(), pos.getY(), pos.getZ(), BlockAirBehavior.AIR_TYPE.getDefaultState());
                        getWorld().sendLevelEventPacket(pos, LevelEvent.BLOCK_STOP_BREAK, 0);
                        getWorld().sendLevelEventPacket(pos, LevelEvent.PARTICLE_DESTROY_BLOCK, oldState.blockStateHash());
                    }
                }
            }
        }

        protected void handleInputData(Set<PlayerAuthInputData> inputData) {
            for (var input : inputData) {
                switch (input) {
                    case START_SPRINTING -> playerEntity.setSprinting(true);
                    case STOP_SPRINTING -> playerEntity.setSprinting(false);
                    case START_SNEAKING -> playerEntity.setSneaking(true);
                    case STOP_SNEAKING -> playerEntity.setSneaking(false);
                    case START_SWIMMING -> playerEntity.setSwimming(true);
                    case STOP_SWIMMING -> playerEntity.setSwimming(false);
                    case START_GLIDING -> playerEntity.setGliding(true);
                    case STOP_GLIDING -> playerEntity.setGliding(false);
                    case START_CRAWLING -> playerEntity.setCrawling(true);
                    case STOP_CRAWLING -> playerEntity.setCrawling(false);
                    case START_JUMPING -> playerEntity.setOnGround(false);
                }
            }
        }
    }
}
