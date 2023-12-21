package cam72cam.immersiverailroading.multiblock;

import java.util.*;

public final class MultiBlockRegistry {

    private static final Map<String, MultiBlock> MULTI_BLOCKS = new HashMap<>();

    private MultiBlockRegistry() {}

    public static void register(String name, MultiBlock multiBlock) {
        MULTI_BLOCKS.put(name, multiBlock);
    }

    public static MultiBlock get(String name) {
        return MULTI_BLOCKS.get(name);
    }

    public static List<String> keys() {
        return new ArrayList<>(MULTI_BLOCKS.keySet());
    }

    public static List<MultiBlock> registered() {
        return new ArrayList<>(MULTI_BLOCKS.values());
    }
}
