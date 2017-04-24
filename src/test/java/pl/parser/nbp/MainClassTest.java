package pl.parser.nbp;


import org.apache.commons.io.IOUtils;
import org.assertj.core.api.Assertions;
import org.custommonkey.xmlunit.XMLUnit;
import org.junit.Test;
import org.w3c.dom.Document;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.InputStream;
import java.io.StringWriter;
import java.time.LocalDate;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.custommonkey.xmlunit.XMLAssert.assertXMLEqual;


/**
 * Created by @Author:piotr.pawliszcze on 19.04.2017.
 */
public class MainClassTest {

    @Test
    public void getXMLDocument() throws Exception {
        InputStream resourceAsStream = this.getClass().getResourceAsStream("/b004z060125.xml");
        String actualXMLString = IOUtils.toString(resourceAsStream, "ISO-8859-2");

        MainClass mainClass = new MainClass();
        Document doc = mainClass.getXMLDocument("http://www.nbp.pl/kursy/xml/b004z060125.xml");

        DOMSource domSource = new DOMSource(doc);
        StringWriter writer = new StringWriter();
        StreamResult result = new StreamResult(writer);
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "ISO-8859-2");
        transformer.transform(domSource, result);
        writer.flush();
        String expectedString = writer.toString();
        XMLUnit.setIgnoreWhitespace(true); // ignore whitespace differences
        assertXMLEqual("error in xml doc ",actualXMLString,expectedString);
     }

    @Test
    public void getPublicationDate() throws Exception {
        MainClass mainClass = new MainClass();
        Document doc = mainClass.getXMLDocument("http://www.nbp.pl/kursy/xml/b004z060125.xml");
        LocalDate publicationDate = mainClass.getPublicationDate(doc);
        assertThat(publicationDate).isEqualByComparingTo(LocalDate.parse("2006-01-25"));
    }



    @Test
    public void testRunParameters()  {
        MainClass.main(new String[]{"EUR", "2013-01-28", "2013-01-31"});
    }

    @Test
    public void testNullOrEmptyRunParameters() {
        Assertions.assertThatThrownBy(() -> MainClass.main(null))
                .as("Testing app with null parameter")
                .hasMessageContaining("Run parameters are null");
    }

    @Test
    public void testIncorrectFirstParameter() {
        Assertions.assertThatThrownBy(() -> MainClass.main(new String[]{"d", "2013-01-28", "2013-01-31"}))
                .as("Testing incorrect first parameter")
                .hasMessageContaining("First parameter should contain only 3 Letters");
    }

    @Test
    public void testIncorrectSecondParameterAsNotDate() {
        Assertions.assertThatThrownBy(() -> MainClass.main(new String[]{"EUR", "2reree", "2013-01-32"}))
                .as("Testing incorrect second parameter")
                .hasMessageContaining("Text '2reree' could not be parsed at index 0");
    }

    @Test
    public void testIncorrectThirdParameterAsNotDate() {
        Assertions.assertThatThrownBy(() -> MainClass.main(new String[]{"EUR", "2013-01-31", "2013-01-39"}))
                .as("Testing incorrect third parameter")
                .hasMessageContaining("Text '2013-01-39' could not be parsed: Invalid value for DayOfMonth (valid values 1 - 28/31): 39");
    }

    @Test
    public void testParameterDatesOrder() {
        Assertions.assertThatThrownBy(() -> MainClass.main(new String[]{"EUR", "2013-01-31", "2013-01-28"}))
                .as("Testing dates order")
                .hasMessageContaining("[Start date, end date rule] Start date is not before end date, start date =2013-01-31 , end date = 2013-01-28");
    }

}