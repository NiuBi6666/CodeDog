package cn.codedog.service;
import cn.codedog.model.*;
import cn.codedog.dao.*;
import cn.codedog.service.RankingScore;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
public class ExamCodeGenerator {
    private static final char[] ALPHABET="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray();
    private final SecureRandom random=new SecureRandom();
    public static boolean valid(String code){
        return code!=null&&code.matches("(?=.*[A-Z])(?=.*[a-z])(?=.*[0-9])[A-Za-z0-9]{8}");
    }
    public String next(){
        while(true){
            char[] code=new char[8];
            for(int i=0;i<code.length;i++)code[i]=ALPHABET[random.nextInt(ALPHABET.length)];
            String value=new String(code);
            if(valid(value))return value;
        }
    }
}
