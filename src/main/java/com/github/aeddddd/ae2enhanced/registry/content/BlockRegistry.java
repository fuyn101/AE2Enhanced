package com.github.aeddddd.ae2enhanced.registry.content;

import com.github.aeddddd.ae2enhanced.block.*;

/**
 * 方块注册表 —— 仅声明 Block 实例字段.
 */
public final class BlockRegistry {

    private BlockRegistry() {}

    // 超因果装配枢纽
    public static BlockAssemblyController ASSEMBLY_CONTROLLER;
    public static BlockAssemblyMeInterface ASSEMBLY_ME_INTERFACE;
    public static BlockAssemblyCasing ASSEMBLY_CASING;
    public static BlockAssemblyInnerWall ASSEMBLY_INNER_WALL;
    public static BlockAssemblyStabilizer ASSEMBLY_STABILIZER;
    public static BlockMicroSingularity MICRO_SINGULARITY;

    // 超维度仓储中枢
    public static BlockHyperdimensionalController HYPERDIMENSIONAL_CONTROLLER;
    public static BlockHyperdimensionalMeInterface HYPERDIMENSIONAL_ME_INTERFACE;
    public static BlockHyperdimensionalCasing HYPERDIMENSIONAL_CASING;
    public static BlockHyperdimensionalSingularityCore HYPERDIMENSIONAL_SINGULARITY_CORE;

    // 超因果计算核心
    public static BlockComputationCore COMPUTATION_CORE;
    public static BlockConstantTensorFieldCasing CONSTANT_TENSOR_FIELD_CASING;
    public static BlockConstantSpinorFieldCasing CONSTANT_SPINOR_FIELD_CASING;
    public static BlockCausalAnchorCore CAUSAL_ANCHOR_CORE;
    public static BlockSuperCraftingInterface SUPER_CRAFTING_INTERFACE;

    // 无线频道系统
    public static BlockWirelessChannelTransmitter WIRELESS_CHANNEL_TRANSMITTER;

    // 中枢 ME 接口
    public static BlockCentralMEInterface CENTRAL_ME_INTERFACE;

    // 智能样板接口
    public static BlockSmartPatternInterface SMART_PATTERN_INTERFACE;

    // RF 访问节点(通用 ME-RF 桥接器,独立于平台系统)
    public static BlockRFAccessNode RF_ACCESS_NODE;

    // 区块供电节点(从 ME 网络向本区块设备供能)
    public static BlockChunkPowerNode CHUNK_POWER_NODE;

    // 压缩区块供电节点(从 ME 网络向 3×3 区块设备供能)
    public static BlockCompressedChunkPowerNode COMPRESSED_CHUNK_POWER_NODE;

    // 先进中枢平台
    public static BlockAdvancedPlatformController ADVANCED_PLATFORM_CONTROLLER;

    // 先进 ME 收集器
    public static BlockAdvancedMECollector ADVANCED_ME_COLLECTOR;
}
