package com.amazon.ion.benchmark;

import com.amazon.ion.IonReader;
import com.amazon.ion.IonType;
import com.amazon.ion.Timestamp;
import com.amazon.ion.system.IonReaderBuilder;
import com.amazon.ion.util.IonStreamUtils;
import org.junit.After;
import org.junit.Test;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class DataGeneratorTest {

    public static String outputFile;

    /**
     * Construct IonReader for current output file in order to finish the following test process
     * @param optionsMap is the hash map which generated by the command line parser which match the option name and its value appropriately.
     * @return constructed IonReader
     * @throws Exception if errors occur during executing data generator process.
     */
    public static IonReader executeAndRead(Map<String, Object> optionsMap) throws Exception {
        outputFile = optionsMap.get("<output_file>").toString();
        GeneratorOptions.executeGenerator(optionsMap);
        return IonReaderBuilder.standard().build(new BufferedInputStream(new FileInputStream(outputFile)));
    }

    /**
     * Assert generated Ion data is the same type as expected.
     * @throws Exception if error occurs when executing Ion data generator.
     */
    @Test
    public void testGeneratedType() throws Exception {
        Map<String, Object> optionsMap = Main.parseArguments("generate", "--data-size", "500", "--data-type", "decimal", "test1.10n");
        try (IonReader reader = DataGeneratorTest.executeAndRead(optionsMap)) {
            while (reader.next() != null) {
                assertSame(reader.getType(), IonType.valueOf(optionsMap.get("--data-type").toString().toUpperCase()));
            }
        }
    }

    /**
     * Assert the exponent range of generated Ion decimals is conform with the expected range.
     * @throws Exception if error occurs when executing Ion data generator.
     */
    @Test
    public void testGeneratedDecimalExponentRange() throws Exception {
        Map<String, Object> optionsMap = Main.parseArguments("generate", "--data-size", "500", "--data-type", "decimal", "--decimal-exponent-range", "[0,10]", "test2.10n");
        try (IonReader reader = DataGeneratorTest.executeAndRead(optionsMap)) {
            List<Integer> range = WriteRandomIonValues.parseRange(optionsMap.get("--decimal-exponent-range").toString());
            while (reader.next() != null) {
                int exp = reader.decimalValue().scale();
                assertTrue(exp * (-1) >= range.get(0) && exp * (-1) <= range.get(1));
            }
        }
    }

    /**
     * Assert the range of coefficient digits number of generated Ion decimals is conform with the expected range.
     * @throws Exception if error occurs when executing Ion data generator.
     */
    @Test
    public void testGeneratedDecimalCoefficientRange() throws Exception {
        Map<String, Object> optionsMap = Main.parseArguments("generate", "--data-size", "500", "--data-type", "decimal", "--decimal-coefficient-digit-range", "[1,12]", "test3.10n");
        try (IonReader reader = DataGeneratorTest.executeAndRead(optionsMap)) {
            List<Integer> range = WriteRandomIonValues.parseRange(optionsMap.get("--decimal-coefficient-digit-range").toString());
            while (reader.next() != null) {
                BigInteger coefficient = reader.decimalValue().unscaledValue();
                double factor = Math.log(2) / Math.log(10);
                int digitCount = (int) (factor * coefficient.bitLength() + 1);
                if (BigInteger.TEN.pow(digitCount - 1).compareTo(coefficient) > 0) {
                    digitCount = digitCount - 1;
                }
                assertTrue(digitCount >= range.get(0) && digitCount <= range.get(1));
            }
        }
    }

    /**
     * Assert the format of generated file is conform with the expected format [ion_binary|ion_text].
     * @throws Exception if error occurs when executing Ion data generator.
     */
    @Test
    public void testGeneratedFormat() throws Exception {
        List<String> inputs = new ArrayList<>(Arrays.asList("ion_text","ion_binary"));
        for (int i = 0; i < 2; i++ ) {
            Map<String, Object> optionsMap = Main.parseArguments("generate", "--data-size", "500", "--data-type", "float", "--format", inputs.get(i), "test4.ion");
            GeneratorOptions.executeGenerator(optionsMap);
            String format = ((List<String>)optionsMap.get("--format")).get(0);
            outputFile = optionsMap.get("<output_file>").toString();
            Path path = Paths.get(outputFile);
            byte[] buffer = Files.readAllBytes(path);
            assertEquals(Format.valueOf(format.toUpperCase()) == Format.ION_BINARY, IonStreamUtils.isIonBinary(buffer));
        }
    }

    /**
     * Assert the unicode code point range of the character which constructed the generated Ion string is conform with the expect range.
     * @throws Exception if error occurs when executing Ion data generator.
     */
    @Test
    public void testGeneratedStringUniCodeRange() throws Exception {
        Map<String, Object> optionsMap = Main.parseArguments("generate", "--data-size", "500", "--data-type", "string", "--text-code-point-range", "[96,99]","test5.10n");
        try (IonReader reader = DataGeneratorTest.executeAndRead(optionsMap)) {
            List<Integer> range = WriteRandomIonValues.parseRange(optionsMap.get("--text-code-point-range").toString());
            while (reader.next() != null) {
                String str = reader.stringValue();
                for (int i = 0; i < str.length(); i++) {
                    int codePoint = Character.codePointAt(str, i);
                    int charCount = Character.charCount(codePoint);
                    //UTF characters may use more than 1 char to be represented
                    if (charCount == 2) {
                        i++;
                    }
                    assertTrue(codePoint >= range.get(0) && codePoint <= range.get(1));
                }
            }
        }
    }

    /**
     * Assert the generated timestamps is follow the precision and proportion of the given timestamp template.
     * @throws Exception if error occurs when executing Ion data generator.
     */
    @Test
    public void testGeneratedTimestampTemplateFormat() throws Exception{
        Map<String, Object> optionsMap = Main.parseArguments("generate", "--data-size", "500", "--data-type", "timestamp", "--timestamps-template", "[2021T]", "test6.10n");
        try (
                IonReader reader = DataGeneratorTest.executeAndRead(optionsMap);
                IonReader templateReader = IonReaderBuilder.standard().build(optionsMap.get("--timestamps-template").toString())
        ) {
            templateReader.next();
            templateReader.stepIn();
            reader.next();
            while (reader.isNullValue()) {
                while (templateReader.next() != null){
                    Timestamp templateTimestamp = templateReader.timestampValue();
                    Timestamp.Precision templatePrecision = templateTimestamp.getPrecision();
                    Timestamp.Precision currentPrecision = reader.timestampValue().getPrecision();
                    Integer templateOffset = templateTimestamp.getLocalOffset();
                    Integer currentOffset = reader.timestampValue().getLocalOffset();
                    assertSame(templatePrecision, currentPrecision);
                    if (currentOffset == null || currentOffset == 0) {
                        assertSame(currentOffset, templateOffset);
                    } else {
                        assertEquals(currentOffset >= -1439 && currentOffset <= 1439, templateOffset >= -1439 && templateOffset <= 1439);
                    }
                    if (currentPrecision == Timestamp.Precision.SECOND) {
                        assertEquals(reader.timestampValue().getDecimalSecond().scale(), templateTimestamp.getDecimalSecond().scale());
                    }
                    reader.next();
                }
            }
        }
    }

    /**
     * Assert the generated data size in bytes has an 10% difference with the expected size, this range is not available for Ion symbol, because the size of symbol is predicted.
     * @throws Exception if error occurs when executing Ion data generator
     */
    @Test
    public void testSizeOfGeneratedData() throws Exception {
        Map <String, Object> optionsMap = Main.parseArguments("generate", "--data-size", "5000", "--data-type", "timestamp", "--timestamps-template","[2021T]","test7.10n");
        GeneratorOptions.executeGenerator(optionsMap);
        int expectedSize = Integer.parseInt(optionsMap.get("--data-size").toString());
        outputFile = optionsMap.get("<output_file>").toString();
        Path filePath = Paths.get(outputFile);
        FileChannel fileChannel;
        fileChannel = FileChannel.open(filePath);
        int fileSize = (int)fileChannel.size();
        fileChannel.close();
        int difference = Math.abs(expectedSize - fileSize);
        assertTrue(difference <= 0.1 * expectedSize);
    }

    /**
     * Delete all files generated in the test process.
     * @throws IOException if an error occur when deleting files.
     */
    @After
    public void deleteGeneratedFile() throws IOException {
        Path filePath = Paths.get(outputFile);
        if(Files.exists(filePath)) {
            Files.delete(filePath);
        }
    }
}
