package cn.codedog.service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
/** Fixed-length lookup keys keep unselected identifiers out of stored/displayed results. */
final class ExamSearchKey {
    private ExamSearchKey(){}
    static String of(List<String> values){
        try{
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            for(String value:values){byte[] bytes=value.strip().getBytes(StandardCharsets.UTF_8);digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array());digest.update(bytes);}
            return HexFormat.of().formatHex(digest.digest());
        }catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
}
