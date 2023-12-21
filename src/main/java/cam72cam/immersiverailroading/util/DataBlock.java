package cam72cam.immersiverailroading.util;

import cam72cam.immersiverailroading.ImmersiveRailroading;
import cam72cam.mod.resource.Identifier;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public interface DataBlock {

    static DataBlock load(Identifier identifier) throws IOException {
        return load(identifier, null, false);
    }

    static DataBlock load(Identifier identifier, DataBlock parameters, boolean useLastInputStream) throws IOException {
        InputStream stream = useLastInputStream ? identifier.getLastResourceStream() : identifier.getResourceStream();

        if (parameters != null) {
            String input = IOUtils.toString(stream, Charset.defaultCharset());
            for (String key : parameters.getValueMap().keySet()) {
                input = input.replace(key, parameters.getValue(key).asString());
            }
            stream = IOUtils.toInputStream(input, Charset.defaultCharset());
        }



        // todo: if file.ext.caml then parseCaml elseif file.ext.json then parseJson else throw error
        //       current approach seems a bit backwards?
        if (identifier.getPath().toLowerCase(Locale.ROOT).endsWith(".caml")) {
            return CAML.parse(stream);
        }
        if (!identifier.getPath().toLowerCase(Locale.ROOT).endsWith(".json")) {
            ImmersiveRailroading.warn("Unexpected file extension '%s', trying JSON...", identifier.toString());
        }
        return JSON.parse(stream);
    }

    Map<String, Value> getValueMap();

    default Value getValue(String key) {
        return this.getValueMap().getOrDefault(key, Value.NULL);
    }

    static DataBlock load(Identifier ident, DataBlock parameters) throws IOException {
        return load(ident, parameters, false);
    }

    default DataBlock getBlock(String key) {
        return this.getBlockMap().get(key);
    }

    Map<String, DataBlock> getBlockMap();

    default List<DataBlock> getBlocks(String key) {
        return this.getBlocksMap().get(key);
    }

    Map<String, List<DataBlock>> getBlocksMap();

    default List<Value> getValues(String key) {
        return this.getValuesMap().get(key);
    }

    Map<String, List<Value>> getValuesMap();

    interface Value {
        Value NULL = new Value() {
            @Override
            public Boolean asBoolean() {
                return null;
            }

            @Override
            public Integer asInteger() {
                return null;
            }

            @Override
            public Float asFloat() {
                return null;
            }

            @Override
            public Double asDouble() {
                return null;
            }

            @Override
            public String asString() {
                return null;
            }
        };

        default boolean asBoolean(boolean fallback) {
            Boolean val = this.asBoolean();
            return val != null ? val : fallback;
        }

        Boolean asBoolean();

        default int asInteger(int fallback) {
            Integer val = this.asInteger();
            return val != null ? val : fallback;
        }

        Integer asInteger();

        default float asFloat(float fallback) {
            Float val = this.asFloat();
            return val != null ? val : fallback;
        }

        Float asFloat();

        default double asDouble(double fallback) {
            Double val = this.asDouble();
            return val != null ? val : fallback;
        }

        Double asDouble();

        default String asString(String fallback) {
            String val = this.asString();
            return val != null ? val : fallback;
        }

        String asString();

        default Identifier asIdentifier(Identifier fallback) {
            Identifier val = this.asIdentifier();
            return val != null && val.canLoad() ? val : fallback;
        }

        default Identifier asIdentifier() {
            String value = this.asString();
            return value != null ? new Identifier(ImmersiveRailroading.MODID, new Identifier(value).getPath()) : null;
        }
    }
}
