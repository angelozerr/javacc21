/* Copyright (c) 2008-2019 Jonathan Revusky, revusky@javacc.com
 * Copyright (c) 2006, Sun Microsystems Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *     * Redistributions of source code must retain the above copyright notices,
 *       this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name Jonathan Revusky, Sun Microsystems, Inc.
 *       nor the names of any contributors may be used to endorse or promote
 *       products derived from this software without specific prior written
 *       permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.javacc.lexgen;

import java.util.*;

import com.javacc.Grammar;
import com.javacc.parsegen.RegularExpression;
import com.javacc.parsegen.TokenSet;
import com.javacc.parser.tree.EndOfFile;
import com.javacc.parser.tree.RegexpChoice;
import com.javacc.parser.tree.RegexpStringLiteral;
import com.javacc.parser.tree.TokenProduction;

/**
 * Generate lexer.
 */
public class LexerData {
    private Grammar grammar;
    private List<LexicalStateData> lexicalStates = new ArrayList<>();
    private List<RegularExpression> regularExpressions = new ArrayList<>();
    private TokenSet skipSet, specialSet, moreSet, tokenSet;
    
    private int stateSetSize;
    boolean hasSkipActions, hasMoreActions, hasSpecial, hasSkip, hasMore;

    private int lohiByteCount;
    private List<NfaState> nonAsciiTableForMethod = new ArrayList<>();
    private Map<String, Integer> lohiByteTable = new HashMap<>();

    private List<String> allBitVectors = new ArrayList<>();
    private int[] tempIndices = new int[512];
    private Map<String, int[]> tableToDump = new HashMap<>();
    private List<int[]> orderedStateSet = new ArrayList<>();
    private int lastIndex;

    public LexerData(Grammar grammar) {
        this.grammar = grammar;
        skipSet = new TokenSet(grammar);
        specialSet = new TokenSet(grammar);
        moreSet = new TokenSet(grammar);
        tokenSet = new TokenSet(grammar);
        RegularExpression reof = new EndOfFile();
        reof.setGrammar(grammar);
        reof.setLabel("EOF");
        regularExpressions.add(reof);
    }
    
    public String getTokenName(int ordinal) {
        return regularExpressions.get(ordinal).getLabel();
    }

    public String getLexicalStateName(int index) {
        return lexicalStates.get(index).getName();
    }

    public void addLexicalState(String name) {
        lexicalStates.add(new LexicalStateData(grammar, name));
    }

    public LexicalStateData getLexicalState(String name) {
        for (LexicalStateData state : lexicalStates) {
            if (state.getName().equals(name)) {
                return state;
            }
        }
        return null;
    }

    public RegularExpression getRegularExpression(int idx) {
        return regularExpressions.get(idx);
    }

    public List<RegularExpression> getRegularExpressions() {
        return regularExpressions;
    }

    public int getLexicalStateIndex(String lexicalStateName) {
        for (int i = 0; i < lexicalStates.size(); i++) {
            LexicalStateData state = lexicalStates.get(i);
            if (state.getName().equals(lexicalStateName)) {
                return i;
            }
        }
        return -1;
    }
    
    public int getNumLexicalStates() {
        return lexicalStates.size();
    }

    public List<LexicalStateData> getLexicalStates() {
        return lexicalStates;
    }

    public void addRegularExpression(RegularExpression regexp) {
        regexp.setOrdinal(regularExpressions.size());
        regularExpressions.add(regexp);
    }
    
    public void ensureStringLabels() {
        for (ListIterator<RegularExpression> it = regularExpressions.listIterator();it.hasNext();) {
            RegularExpression regexp = it.next();
            if (!isJavaIdentifier(regexp.getLabel())) {
                String label = "_TOKEN_" + it.previousIndex();
                if (regexp instanceof RegexpStringLiteral) {
                    String s= ((RegexpStringLiteral)regexp).getImage().toUpperCase();
                    if (isJavaIdentifier(s) && !regexpLabelAlreadyUsed(s)) label = s;
                }
                regexp.setLabel(label);
            }
        }
    }
   
    static public boolean isJavaIdentifier(String s) {
        if (s.length() == 0) return false;
        if (!Character.isJavaIdentifierStart(s.charAt(0))) return false;
        for (int i=1; i<s.length(); i++) {
            if (!Character.isJavaIdentifierPart(s.charAt(i))) return false;
        }
        return true;
    }
   
    private boolean regexpLabelAlreadyUsed(String label) {
        for (RegularExpression regexp : regularExpressions) {
            if (label.contentEquals(regexp.getLabel())) return true;
        }
        return false;
    }

    
    public String getStringLiteralLabel(String image) {
        for (RegularExpression regexp : regularExpressions) {
            if (regexp instanceof RegexpStringLiteral) {
                if (((RegexpStringLiteral) regexp).getImage().equals(image)) {
                    return regexp.getLabel();
                }
            }
        }
        return null;
    }

    public int getTokenCount() {
        return regularExpressions.size();
    }

    public boolean getHasMore() {
        return hasMore;
    }

    public boolean getHasMoreActions() {
        return moreSet.cardinality() >0;
    }

    public boolean getHasSpecial() {
        return hasSpecial;
    }

    public boolean getHasSkip() {
        return hasSkip;
    }

    public boolean getHasSkipActions() {
        return hasSkipActions;
    }

    public boolean hasTokenAction(int index) {
        return tokenSet.get(index);
    }

    public boolean hasMoreAction(int index) {
        return moreSet.get(index);
    }

    public boolean hasSkipAction(int index) { 
        return skipSet.get(index);
    }

    public BitSet getMoreSet() {
        return moreSet;
    } 

    public BitSet getTokenSet() {
        return tokenSet;
    }
    
    public BitSet getSkipSet() {
        return skipSet;
    }
    
    public BitSet getSpecialSet() {
        return specialSet;
    }

    public int getStateSetSize() {
        return stateSetSize;
    }

    public List<NfaState> getNonAsciiTableForMethod() {
        return nonAsciiTableForMethod;
    }

    int getLohiByteCount() {
        return lohiByteCount;
    }

    void incrementLohiByteCount() {
        ++lohiByteCount;
    }

    Map<String, Integer> getLoHiByteTable() {
        return lohiByteTable;
    }

    public List<String> getAllBitVectors() {
        return allBitVectors;
    }

    int[] getTempIndices() {
        return tempIndices;
    }

    Map<String, int[]> getTableToDump() {
        return tableToDump;
    }

    public List<int[]> getOrderedStateSet() {
        return orderedStateSet;
    }

    int getLastIndex() {
        return lastIndex;
    }

    void setLastIndex(int i) {
        lastIndex = i;
    }

    public int getIndex(String name) {
        for (int i = 0; i < lexicalStates.size(); i++) {
            if (lexicalStates.get(i).getName().equals(name)) {
                return i;
            }
        }
        return -1;
    }

    public void buildData() {
        for (TokenProduction tokenProduction : grammar.descendants(TokenProduction.class)) {
            for (String lexStateName : tokenProduction.getLexicalStateNames()) {
                LexicalStateData lexState = getLexicalState(lexStateName);
                lexState.addTokenProduction(tokenProduction);
            }
        }
        tokenSet.set(0);
        List<RegexpChoice> choices = new ArrayList<RegexpChoice>();
        for (LexicalStateData lexState : lexicalStates) {
            choices.addAll(lexState.process());
        }
        for (RegexpChoice choice : choices) {
            checkUnmatchability(choice);
        }
    }

    void expandStateSetSize(int size) {
        if (stateSetSize < size) stateSetSize = size;
    }

    //What about the case of a regexp existing in multiple lexical states? REVISIT (JR)
    static public void checkUnmatchability(RegexpChoice choice) {
        for (RegularExpression curRE : choice.getChoices()) {
            if (!(curRE).isPrivate() && curRE.getOrdinal() > 0 && curRE.getOrdinal() < choice.getOrdinal()
                    && curRE.getLexicalState() == choice.getLexicalState()) {
                choice.getGrammar().addWarning(choice, "Regular Expression choice : " + curRE.getLabel()
                        + " can never be matched as : " + choice.getLabel());
            }
        }
    }
}
