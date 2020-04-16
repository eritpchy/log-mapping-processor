package net.xdow.logmapping.util;

import com.google.gson.GsonBuilder;
import net.xdow.logmapping.bean.MappingInfo;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Optional;

public class EncodeUtils {

    public static int encode(HashMap<Integer, MappingInfo> map, String value) {
        int key = value.hashCode() ^ value.length();
        int retryCount = 10;
        int i;
        for (i = 0; i < 10; i++) {
            if (!map.containsKey(key)) {
                break;
            }
            if (value.equals(Optional.ofNullable(map.get(key)).map(v -> v.value).orElse( null))) {
                break;
            }
            key = key + 1;
        }
        if (i >= retryCount && map.containsKey(key)) {
            throw new IllegalArgumentException("map key is not unique, key:" + key);
        }
        return key;
    }

    public static void addToEncodedMap(HashMap<Integer, MappingInfo> map, int key, MappingInfo value) {
        L.d("add to encoded map key:" + key + " value:" + value);
        map.put(key, value);
    }

    public static void saveEncodedMap(HashMap<Integer, MappingInfo> map, File targetFile) throws IOException {
        String json = new GsonBuilder().setPrettyPrinting().create().toJson(map);
        Files.write(targetFile.toPath(), json.getBytes());
    }
}
