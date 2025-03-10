package com.amazon.ion.benchmark;

import com.amazon.ion.IonDatagram;
import com.amazon.ion.IonList;
import com.amazon.ion.IonLoader;
import com.amazon.ion.IonReader;
import com.amazon.ion.IonStruct;
import com.amazon.ion.IonSystem;
import com.amazon.ion.IonType;
import com.amazon.ion.IonValue;
import com.amazon.ion.system.IonReaderBuilder;
import com.amazon.ion.system.IonSystemBuilder;

import java.io.BufferedInputStream;
import java.io.FileInputStream;

/**
 * Parse Ion Schema file and get the general constraints in the file then pass the constraints to the Ion data generator.
 */
public class ReadGeneralConstraints {
    private static final IonSystem system = IonSystemBuilder.standard().build();
    private static final IonLoader loader = system.newLoader();
    private static final String defaultRange = "[0, 1114111]";

    /**
     * Get general constraints of Ion Schema and call the relevant generator method based on the type.
     * @param size is the size of the output file.
     * @param path is the path of the Ion Schema file.
     * @param format is the format of the generated file, select from set (ion_text | ion_binary).
     * @param outputFile is the path of the generated file.
     * @throws Exception if errors occur when reading and writing data.
     */
    public static void readIonSchemaAndGenerate(int size, String path, String format, String outputFile) throws Exception {
        try (IonReader reader = IonReaderBuilder.standard().build(new BufferedInputStream(new FileInputStream(path)))) {
            IonDatagram schema = loader.load(reader);
            for (int i = 0; i < schema.size(); i++) {
                IonValue schemaValue = schema.get(i);
                // Assume there's only one constraint between schema_header and schema_footer, if more constraints added, here is the point where developers should start.
                if (schemaValue.getType().equals(IonType.STRUCT) && schemaValue.getTypeAnnotations()[0].equals(IonSchemaUtilities.keyWordType)) {
                    IonStruct constraintStruct = (IonStruct) schemaValue;
                    // Get general constraints:
                    IonType type = IonType.valueOf(constraintStruct.get(IonSchemaUtilities.keyWordType).toString().toUpperCase());
                    IonList annotations = IonSchemaUtilities.getAnnotation(constraintStruct);
                    // Get more specific constraints according to which type of Ion data needed to be generated
                    // If more types of Ion data added in the future, developers can add more types under the switch logic.
                    switch (type) {
                        case STRUCT:
                            IonStruct fields = (IonStruct) constraintStruct.get(IonSchemaUtilities.keyWordFields);
                            WriteRandomIonValues.writeRandomStructValues(size, format, outputFile, fields, annotations);
                            break;
                        case TIMESTAMP:
                            WriteRandomIonValues.writeRandomTimestamps(size, type, outputFile, null, format);
                            break;
                        case STRING:
                            int codePointBoundary = IonSchemaUtilities.parseConstraints(constraintStruct, IonSchemaUtilities.keyWordCodePointLength);
                            WriteRandomIonValues.writeRandomStrings(size, type, outputFile, defaultRange, format, codePointBoundary);
                            break;
                        case DECIMAL:
                            break;
                        case INT:
                            WriteRandomIonValues.writeRandomInts(size, type, format, outputFile);
                            break;
                        case FLOAT:
                            WriteRandomIonValues.writeRandomFloats(size, type, format, outputFile);
                            break;
                        case BLOB:
                        case CLOB:
                            WriteRandomIonValues.writeRandomLobs(size, type, format, outputFile);
                            break;
                        case SYMBOL:
                            WriteRandomIonValues.writeRandomSymbolValues(size, format, outputFile);
                            break;
                    }
                }
            }
        }
    }

}
