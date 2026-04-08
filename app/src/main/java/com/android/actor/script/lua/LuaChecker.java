package com.android.actor.script.lua;

import org.luaj.vm2.ast.Chunk;
import org.luaj.vm2.ast.Exp;
import org.luaj.vm2.ast.Stat;
import org.luaj.vm2.ast.Visitor;
import org.luaj.vm2.parser.LuaParser;
import org.luaj.vm2.parser.ParseException;
import org.luaj.vm2.parser.TokenMgrError;

import java.io.InputStream;

public class LuaChecker {
    private final static String TAG = LuaChecker.class.getSimpleName();

    public static void parseLuaStream(InputStream is, LuaLogger logger) {
        try {
            LuaParser parser = new LuaParser(is);
            // Perform the parsing.
            Chunk chunk = parser.Chunk();
            // Print out line info for all function definitions.
            chunk.accept(new Visitor() {
                public void visit(Exp.AnonFuncDef exp) {
                    if (logger != null) {
                        logger.i(TAG, "Anonymous function definition at "
                                + exp.beginLine + "." + exp.beginColumn + ","
                                + exp.endLine + "." + exp.endColumn);
                    }
                }

                public void visit(Stat.FuncDef stat) {
                    if (logger != null) {
                        logger.i(TAG, "Function definition '" + stat.name.name.name + "' at "
                                + stat.beginLine + "." + stat.beginColumn + ","
                                + stat.endLine + "." + stat.endColumn);
                        logger.i(TAG, "\tName location "
                                + stat.name.beginLine + "." + stat.name.beginColumn + ","
                                + stat.name.endLine + "." + stat.name.endColumn);
                    }
                }

                public void visit(Stat.LocalFuncDef stat) {
                    if (logger != null) {
                        logger.i(TAG, "Local function definition '" + stat.name.name + "' at "
                                + stat.beginLine + "." + stat.beginColumn + ","
                                + stat.endLine + "." + stat.endColumn);
                    }
                }
            });
        } catch (ParseException e) {
            if (logger != null) {
                logger.i(TAG, "parse failed: " + e.getMessage() + "\n"
                        + "Token Image: '" + e.currentToken.image + "'\n"
                        + "Location: " + e.currentToken.beginLine + ":" + e.currentToken.beginColumn
                        + "-" + e.currentToken.endLine + "," + e.currentToken.endColumn);
            }
        } catch (TokenMgrError e) {
            if (logger != null) {
                logger.i(TAG, "parse failed: " + e.getMessage());
            }
        }
    }
}
