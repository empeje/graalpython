/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.pegparser;

import com.oracle.graal.python.pegparser.sst.SSTNode;
import com.oracle.graal.python.pegparser.sst.VarLookupSSTNode;
import com.oracle.graal.python.pegparser.tokenizer.Token;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * From this class is extended the generated parser. It allow access to the
 * tokenizer.  The methods defined in this class are mostly equivalents to those
 * defined in CPython's {@code pegen.c}. This allows us to keep the actions and
 * parser generator very similar to CPython for easier updating in the future.
 */
public abstract class AbstractParser {
    //TODO This should be package protected
    protected static final Set<String> softKeywords = new HashSet<>();
    protected static final Map<String, Integer> reservedKeywords = new LinkedHashMap<>();

    private final ParserTokenizer tokenizer;
    protected final NodeFactory factory;

    protected int level = 0;
    protected boolean callInvalidRules = false;
    private VarLookupSSTNode cachedDummyName;

    protected final RuleResultCache<Object> cache = new RuleResultCache(this);
    protected final Map<Integer, String> comments = new LinkedHashMap<>();

    public AbstractParser(ParserTokenizer tokenizer, NodeFactory factory) {
        this.tokenizer = tokenizer;
        this.factory = factory;
    }

    /**
     * Get position in the tokenizer.
     * @return the position in tokenizer.
     */
    public int mark() {
        return tokenizer.mark();
    }

    /**
     * Reset position in the tokenizer
     * @param position where the tokenizer should set the current position
     */
    public void reset(int position) {
        tokenizer.reset(position);
    }

    /**
     * Is the expected token on the current position in tokenizer? If there is the
     * expected token, then the current position in tokenizer is changed to the next token.
     * @param tokenKind - the token kind that is expected on the current position
     * @return The expected token or null if the token on the current position is not
     * the expected one.
     */
    public Token expect(int tokenKind) {
        Token token = tokenizer.peekToken();
        if (token.type == tokenKind) {
            return tokenizer.getToken();
        }
        return null;
    }

    /**
     * Is the expected token on the current position in tokenizer? If there is the
     * expected token, then the current position in tokenizer is changed to the next token.
     * @param text - the token on the current position has to have this text
     * @return The expected token or null if the token on the current position is not
     * the expected one.
     */
    public Token expect(String text) {
        Token token = tokenizer.peekToken();
        if (text.equals(tokenizer.getText(token))) {
            return tokenizer.getToken();
        }
        return null;
    }

    /**
     * Check if the next token that'll be read is if the expected kind. This has
     * does not advance the tokenizer, in contrast to {@link expect(int)}.
     */
    protected boolean lookahead(boolean match, int kind) {
        int pos = mark();
        Token token = expect(kind);
        reset(pos);
        return (token != null) == match;
    }

    /**
     * Check if the next token that'll be read is if the expected kind. This has
     * does not advance the tokenizer, in contrast to {@link expect(String)}.
     */
    protected boolean lookahead(boolean match, String text) {
        int pos = mark();
        Token token = expect(text);
        reset(pos);
        return (token != null) == match;
    }

    /**
     * Shortcut to Tokenizer.getText(Token)
     * @param token
     * @return
     */
    public String getText(Token token) {
        return tokenizer.getText(token);
    }


    public Token getToken(int pos) {
        if (pos > tokenizer.mark()) {
            throw new RuntimeException("getToken(pos) can be used only for position that is already scanned!");
        }
        int helpPos = mark();
        Token token =  tokenizer.peekToken();
        
        tokenizer.reset(pos);
        return token;
    }

    /**
     * equivalent to PyPegen_fill_token in that it modifies the token
     */
    public Token getAndInitializeToken() {
        int pos = mark();
        Token token = getToken(pos);
        while (token.type == Token.Kind.TYPE_IGNORE) {
            String tag = getText(token);
            comments.put(token.startLine, tag);
            token = getToken(pos);
        }

        // TODO: handle reaching end in single_input mode
        
        return initializeToken(token);
    }

    public Token getLastNonWhitespaceToken() {
        Token t = null;
        for (int i = mark() - 1; i >= 0; i--) {
            t = tokenizer.peekToken(i);
            if (t.type != Token.Kind.ENDMARKER && (t.type < Token.Kind.NEWLINE || t.type > Token.Kind.DEDENT)) {
                break;
            }
        }
        return t;
    }

    public SSTNode name_token() {
        Token t = expect(Token.Kind.NAME);
        if (t != null) {            
            return factory.createVariable(getText(t), t.startOffset, t.endOffset);
        } else {
            return null;
        }
    }

    protected SSTNode expect_SOFT_KEYWORD(String keyword) {
        Token t = tokenizer.peekToken();
        if (t.type == Token.Kind.NAME && getText(t).equals(keyword)) {
            tokenizer.getToken();
            return factory.createVariable(getText(t), t.startOffset, t.endOffset);
        }
        return null;
    }

    /**
     * IMPORTANT! _PyPegen_string_token returns (through void*) a Token*. But
     * that's actually only used in rules that then create a proper string out
     * of multiple tokens. So we'll adapt those and return a string constant
     * node here.
     */
    public SSTNode string_token() {
        Token t = expect(Token.Kind.STRING);
        if (t == null) {
            return null;
        }
        return factory.createString(getText(t), t.startOffset, t.endOffset);
    }

    public SSTNode number_token() {
        Token t = expect(Token.Kind.NUMBER);
        if (t != null) {            
            return factory.createNumber(getText(t), t.startOffset, t.endOffset);
        } else {
            return null;
        }
    }

    public Token expect_forced_token(int kind, String msg) {
        Token t = getAndInitializeToken();
        if (t.type != kind) {
            // TODO: raise error
            return null;
        }
        return t;
    }

    public SSTNode name_from_token(Token t) {
        if (t == null) {
            return null;
        }
        String id = getText(t);
        return factory.createVariable(id, t.startOffset, t.endOffset);
    }

    public SSTNode soft_keyword_token() {
        Token t = expect(Token.Kind.NAME);
        if (t == null) {
            return null;
        }
        String txt = getText(t);
        if (softKeywords.contains(txt)) {
            return name_from_token(t);
        }
        return null;
    }

    public SSTNode dummyName(Object... args) {
        if (cachedDummyName != null) {
            return cachedDummyName;
        }
        cachedDummyName = factory.createVariable("", 0, 0);
        return cachedDummyName;
    }

    public Object[] insertInFront(Object element, Object[] seq) {
        Object[] result = new Object[seq.length + 1];
        System.arraycopy(seq, 0, result, 1, seq.length);
        result[0] = element;
        return result;
    }

    /**
     * equivalent to initialize_token
     */
    private Token initializeToken(Token token) {
        if (token.type == Token.Kind.NAME) {
            String txt = getText(token);
            if (reservedKeywords.containsKey(txt)) {
                token.type = (int)reservedKeywords.get(txt);
            }
        }
        return token;
    }

    // debug methods
    private void indent(StringBuffer sb) {
        for (int i = 0; i < level; i++) {
            sb.append("  ");
        }
    }

    void debugMessage(String text) {
        debugMessage(text, true);
    }

    void debugMessage(String text, boolean indent) {
        StringBuffer sb = new StringBuffer();
        indent(sb);
        sb.append(text);
        System.out.print(sb.toString());
    }

    void debugMessageln(String text, Object... args) {
        StringBuffer sb = new StringBuffer();
        indent(sb);
        sb.append(String.format(text, args));
        System.out.println(sb.toString());
    }
}
