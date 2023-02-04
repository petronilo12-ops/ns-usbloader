/*
    Copyright 2018-2023 Dmitry Isaenko

    This file is part of NS-USBloader.

    NS-USBloader is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    NS-USBloader is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with NS-USBloader.  If not, see <https://www.gnu.org/licenses/>.
 */
package nsusbloader.Utilities.patches.fs.finders;

import libKonogonka.Converter;
import nsusbloader.Utilities.patches.AHeuristic;
import nsusbloader.Utilities.patches.BinToAsmPrinter;
import nsusbloader.Utilities.patches.SimplyFind;

import java.util.ArrayList;
import java.util.List;

class HeuristicFs2 extends AHeuristic {
    private static final String PATTERN = "...94081c00121f050071..0054"; // "...94"->BL "081c0012"->AND "1f050071"->CMP "..0054"->B.cond (only '54' is signature!)

    private final byte[] where;
    private final List<Integer> findings;

    HeuristicFs2(byte[] where){
        this.where = where;
        this.findings = new ArrayList<>();
        SimplyFind simplyfind = new SimplyFind(PATTERN, where);
        simplyfind.getResults().forEach(var -> findings.add(var + 4));

        if(findings.size() < 2)
            return;

        this.findings.removeIf(this::dropStep1);
    }
    // All matches are somewhere in 0x6xxxx-0x7xxxx, then let's just limit search field by 0x80000
    private boolean dropStep1(int offsetOfPatternFound){
        return (offsetOfPatternFound > 0x80000);
    }

    @Override
    public boolean isFound(){
        return findings.size() == 1;
    }

    @Override
    public boolean wantLessEntropy(){
        return findings.size() > 1;
    }

    @Override
    public int getOffset() throws Exception{
        if(findings.isEmpty())
            throw new Exception("Nothing found");
        if (findings.size() > 1)
            throw new Exception("Too many offsets");
        return findings.get(0);
    }

    @Override
    public boolean setOffsetsNearby(int offsetNearby) {
        findings.removeIf(offset -> {
            if (offset > offsetNearby)
                return ! (offset < offsetNearby - 0xffff);
            return ! (offset > offsetNearby - 0xffff);
        });
        return isFound();
    }

    @Override
    public String getDetails(){
        int offsetInternal = findings.get(0) - 4;
        int firstExpression = Converter.getLEint(where, offsetInternal);
        int conditionalJumpLocation = ((firstExpression & 0x3ffffff) * 4 + offsetInternal) & 0xfffff;
        int secondExpressionsPairElement1 = Converter.getLEint(where, conditionalJumpLocation);
        int secondExpressionsPairElement2 = Converter.getLEint(where, conditionalJumpLocation+4);

        return BinToAsmPrinter.printSimplified(firstExpression, offsetInternal) +
                BinToAsmPrinter.printSimplified(Converter.getLEint(where, offsetInternal + 4), offsetInternal + 4) +
                BinToAsmPrinter.printSimplified(Converter.getLEint(where, offsetInternal + 8), offsetInternal + 8) +
                BinToAsmPrinter.printSimplified(Converter.getLEint(where, offsetInternal + 12), offsetInternal + 12) +
                "...\n" +
                BinToAsmPrinter.printSimplified(secondExpressionsPairElement1, conditionalJumpLocation) +
                BinToAsmPrinter.printSimplified(secondExpressionsPairElement2, conditionalJumpLocation + 4);
    }
}
