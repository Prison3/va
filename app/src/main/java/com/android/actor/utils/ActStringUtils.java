package com.android.actor.utils;

import android.annotation.SuppressLint;
import com.android.actor.monitor.Logger;

import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ActStringUtils {

    private final static String TAG = ActStringUtils.class.getSimpleName();

    @SuppressLint("SimpleDateFormat")
    private final static SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private final static String PROXY_REGEX = "((?<protocol>http|socks|socks5)://)?(?:(?:(?<user>[a-zA-Z0-9._\\-@!&#%$]{1,180}):(?<pwd>[a-zA-Z0-9=._\\-@!&#%$]{0,}))@)?(?<ip>[a-zA-Z0-9][-a-zA-Z0-9]{0,62}(?:.[a-zA-Z0-9][-a-zA-Z0-9]{0,62})+).?:(?<port>\\d{1,5})";
    private final static Pattern IP_PATTERN = Pattern.compile("^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$");

    public static boolean isEmpty(String str) {
        return str == null || str.length() == 0;
    }

    public static String listToString(List<String> list) {
        StringBuilder builder = new StringBuilder();
        for (String str : list) {
            builder.append(str).append("\n");
        }
        return builder.toString();
    }

    public static String arrayToString(Object[] array) {
        StringBuilder builder = new StringBuilder();
        for (Object str : array) {
            builder.append(str).append("\n");
        }
        return builder.toString();
    }

    public static String exceptionToString(Exception e) {
        StringBuilder builder = new StringBuilder(e.toString()).append("\n");
        for (StackTraceElement element : e.getStackTrace()) {
            builder.append(element.toString()).append("\n");
        }
        return builder.toString();
    }


    public static String intToIp(long ipInt) {
        StringBuilder sb = new StringBuilder();
        sb.append(ipInt & 0xFF).append(".");
        sb.append((ipInt >> 8) & 0xFF).append(".");
        sb.append((ipInt >> 16) & 0xFF).append(".");
        sb.append((ipInt >> 24) & 0xFF);
        return sb.toString();
    }

    public static boolean isPositiveNumeric(String str) {
        Pattern pattern = Pattern.compile("[0-9]*");
        Matcher isNum = pattern.matcher(str);
        return isNum.matches();
    }


    public static boolean checkProxyPattern(String input) {
        return matchProxyPattern(input) != null;
    }

    public static Matcher matchProxyPattern(String input) {
        Matcher m = Pattern.compile(PROXY_REGEX).matcher(input);
        if (m.matches()) {
            return m;
        }
        return null;
    }

    public static boolean isIP(String input) {
        return IP_PATTERN.matcher(input).matches();
    }

    public static String getStandardTime() {
        return TIME_FORMAT.format(new Date().getTime());
    }

    public static String safeCharSeqToString(CharSequence cs) {
        if (cs == null)
            return "";
        else {
            return stripInvalidXMLChars(cs);
        }
    }

    private static String stripInvalidXMLChars(CharSequence cs) {
        StringBuffer ret = new StringBuffer();
        char ch;
        /* http://www.w3.org/TR/xml11/#charsets
        [#x1-#x8], [#xB-#xC], [#xE-#x1F], [#x7F-#x84], [#x86-#x9F], [#xFDD0-#xFDDF],
        [#x1FFFE-#x1FFFF], [#x2FFFE-#x2FFFF], [#x3FFFE-#x3FFFF],
        [#x4FFFE-#x4FFFF], [#x5FFFE-#x5FFFF], [#x6FFFE-#x6FFFF],
        [#x7FFFE-#x7FFFF], [#x8FFFE-#x8FFFF], [#x9FFFE-#x9FFFF],
        [#xAFFFE-#xAFFFF], [#xBFFFE-#xBFFFF], [#xCFFFE-#xCFFFF],
        [#xDFFFE-#xDFFFF], [#xEFFFE-#xEFFFF], [#xFFFFE-#xFFFFF],
        [#x10FFFE-#x10FFFF].
         */
        for (int i = 0; i < cs.length(); i++) {
            ch = cs.charAt(i);

            if ((ch >= 0x1 && ch <= 0x8) || (ch >= 0xB && ch <= 0xC) || (ch >= 0xE && ch <= 0x1F) ||
                    (ch >= 0x7F && ch <= 0x84) || (ch >= 0x86 && ch <= 0x9f) ||
                    (ch >= 0xFDD0 && ch <= 0xFDDF) || (ch >= 0x1FFFE && ch <= 0x1FFFF) ||
                    (ch >= 0x2FFFE && ch <= 0x2FFFF) || (ch >= 0x3FFFE && ch <= 0x3FFFF) ||
                    (ch >= 0x4FFFE && ch <= 0x4FFFF) || (ch >= 0x5FFFE && ch <= 0x5FFFF) ||
                    (ch >= 0x6FFFE && ch <= 0x6FFFF) || (ch >= 0x7FFFE && ch <= 0x7FFFF) ||
                    (ch >= 0x8FFFE && ch <= 0x8FFFF) || (ch >= 0x9FFFE && ch <= 0x9FFFF) ||
                    (ch >= 0xAFFFE && ch <= 0xAFFFF) || (ch >= 0xBFFFE && ch <= 0xBFFFF) ||
                    (ch >= 0xCFFFE && ch <= 0xCFFFF) || (ch >= 0xDFFFE && ch <= 0xDFFFF) ||
                    (ch >= 0xEFFFE && ch <= 0xEFFFF) || (ch >= 0xFFFFE && ch <= 0xFFFFF) ||
                    (ch >= 0x10FFFE && ch <= 0x10FFFF))
                ret.append(".");
            else
                ret.append(ch);
        }
        return ret.toString();
    }

    public static String md5(String str) {
        return md5(str.getBytes(Charset.defaultCharset()));
    }

    public static String md5(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            return bytesToHex(md.digest(bytes));
        } catch (Exception e) {
            Logger.e(TAG, "md5: " + e.toString(), e);
        }
        return null;
    }

    private static final byte[] HEX_ARRAY = "0123456789ABCDEF".getBytes();

    public static String bytesToHex(byte[] bytes) {
        byte[] hexChars = new byte[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars, Charset.defaultCharset());
    }

    public static String checkUrlPattern(String url, String host) {
        Logger.d(TAG, "checkUrlPattern: " + url + " host: " + host);
        Pattern pattern = Pattern.compile("https://" + host + "/([^?]+)");
        Matcher matcher = pattern.matcher(url);
        if (matcher.find()) {
            Logger.d(TAG, "checkUrlPattern ok : " + matcher.group(1));
            return matcher.group(1);
        } else {
            Logger.d(TAG, "checkUrlPattern failed , return null ");
            return "";
        }

    }
}