/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.parser;

// TODO this class has to be moved to impl package and from this package we need to do api. 

import com.oracle.graal.python.parser.sst.NumberLiteralSSTNode;
import com.oracle.graal.python.parser.sst.SSTNode;
import com.oracle.graal.python.parser.sst.VarLookupSSTNode;


public class NodeFactoryImp implements NodeFactory{

    @Override
    public VarLookupSSTNode createVariable(String name, int startOffset, int endOffset) {
        return new VarLookupSSTNode(name, startOffset, endOffset);
    }

    @Override
    public SSTNode createNumber(String number, int startOffset, int endOffset) {
        // TODO handle all kind of numbers here. 
        return NumberLiteralSSTNode.create(number, 0, 10, startOffset, endOffset);
    }
    
    
    
}
