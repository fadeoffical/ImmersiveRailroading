package cam72cam.immersiverailroading.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MergedBlocks implements DataBlock {
    Map<String, DataBlock.Value> primitives;
    Map<String, List<DataBlock.Value>> primitiveSets;
    Map<String, DataBlock> blocks;
    Map<String, List<DataBlock>> blockSets;

    public MergedBlocks(DataBlock base, DataBlock override) {
        this.primitives = new LinkedHashMap<>(base.getValueMap());
        this.primitiveSets = new LinkedHashMap<>(base.getValuesMap());
        this.blocks = new LinkedHashMap<>(base.getBlockMap());
        this.blockSets = new LinkedHashMap<>(base.getBlocksMap());

        this.primitives.putAll(override.getValueMap());
        override.getValuesMap().forEach((key, values) -> {
            if (this.primitiveSets.containsKey(key)) {
                // Merge into new list
                List<Value> tmp = new ArrayList<>(this.primitiveSets.get(key));
                tmp.addAll(values);
                values = tmp;
            }
            this.primitiveSets.put(key, values);
        });
        override.getBlockMap().forEach((key, block) -> {
            if (this.blocks.containsKey(key)) {
                block = new MergedBlocks(this.blocks.get(key), block);
            }
            this.blocks.put(key, block);
        });
        override.getBlocksMap().forEach((key, blocks) -> {
            if (this.blockSets.containsKey(key)) {
                List<DataBlock> tmp = new ArrayList<>(this.blockSets.get(key));
                tmp.addAll(blocks);
                blocks = tmp;
            }
            this.blockSets.put(key, blocks);
        });
    }

    @Override
    public Map<String, Value> getValueMap() {
        return this.primitives;
    }

    @Override
    public Map<String, DataBlock> getBlockMap() {
        return this.blocks;
    }

    @Override
    public Map<String, List<DataBlock>> getBlocksMap() {
        return this.blockSets;
    }

    @Override
    public Map<String, List<Value>> getValuesMap() {
        return this.primitiveSets;
    }
}
