package pl.parser.nbp;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.text.NumberFormat;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Java6Assertions.assertThat;

/**
 * Created by @Author:piotr.pawliszcze on 19.04.2017.
 */
@Slf4j
public class MainClass {

    //First parameter
    private static final int SECOND_PARAM = 1;

    //Second parameter
    private static final int THIRD_PARAM = 2;

    //Third parameter
    private static final int FIRST_PARAM = 0;

    //Number of parameter allowed and necessary to run application
    private static final int ACCEPTED_NUMBER_OF_PARAMETERS = 3;

    //String pattern for currency symbol
    private static final String THREE_LETTER_CURRENCY_SYMBOL = "\\b[a-zA-Z]{3}\\b";

    //adres url z lista plikow dla nadego roku
    private static final String NBP_KURSY_DIR = "http://www.nbp.pl/kursy/xml/dir%s.txt";

    //adres url do pobierania kursow sprzedazy i kupna na dany dzien
    private static final String NBP_KURSY_SREDNIE_XML = "http://www.nbp.pl/kursy/xml/%s.xml";
    //TAGS
    private static final String PUBLICATION_DATE_TAG = "data_publikacji";
    private static final String KOD_WALUTY_TAG = "kod_waluty";

    private NumberFormat nf = NumberFormat.getInstance(Locale.FRANCE);

    public static void main(String[] args) {

        MainClass mainClass = new MainClass();
        mainClass.validateParameters(args);

        String startDate = args[SECOND_PARAM];
        String endDate = args[THIRD_PARAM];
        List<String> exchangeRateTableNameList = mainClass.getExchangeRateTableListForDates(startDate, endDate);

        double avgBuyRate = 0;
        double ex2 =0 ;
        double ex= 0;


        for (String tableName : exchangeRateTableNameList) {

            Document doc = mainClass.getXMLDocument(String.format(NBP_KURSY_SREDNIE_XML,tableName).replace(StringUtils.CR, StringUtils.EMPTY));
            Node positionNode = mainClass.getPositionNodeFromCurrency(doc, args[FIRST_PARAM]);
            double sellRate = mainClass.getRateFromPosition(positionNode, RateType.SELL_RATE);
            ex += sellRate;
            ex2 += Math.pow(sellRate,2);
            avgBuyRate += mainClass.getRateFromPosition(positionNode, RateType.BUY_RATE);
        }

        int numberOfItems = exchangeRateTableNameList.size();

        System.out.printf("%,.4f\n",avgBuyRate / numberOfItems);
        System.out.printf("%,.4f\n", Math.sqrt((ex2 / numberOfItems) - Math.pow(ex/numberOfItems,2)));

    }

    /**
     * Method return list of exchange rate tables file names
     *
     * @param startDate start date as {@link String}
     * @param endDate   end date as {@link String}
     * @return List of table names as {@link String}
     */
    private List<String> getExchangeRateTableListForDates(String startDate, String endDate) {
        LocalDate startLocalDate = LocalDate.parse(startDate);
        LocalDate endLocalDate = LocalDate.parse(endDate);

        DateTimeFormatter df = DateTimeFormatter.ofPattern("yyMMdd");
        return Stream.iterate(startLocalDate, date -> date.plusDays(1)).limit(ChronoUnit.DAYS.between(startLocalDate, endLocalDate) + 1).map(localDate -> {

            try {
                String year = LocalDate.now().getYear() == localDate.getYear() ? StringUtils.EMPTY : String.valueOf(localDate.getYear());
                URL tablesURL = new URL(String.format(NBP_KURSY_DIR, year));
                String currencyTableAsString = IOUtils.toString(tablesURL, Charset.defaultCharset());
                Optional<String> table = Stream.of(currencyTableAsString.split(System.lineSeparator())).filter(s -> s.contains(df.format(localDate))).findFirst();
                if (table.isPresent()) {
                    return table.get();
                }

            } catch (IOException e) {
                throw new RuntimeException("Error during create exchange rate tables list", e);
            }
            return null;

        }).collect(Collectors.toList());
    }


    /**
     * Method return XML document
     *
     * @param url - url {@link URL}
     * @return document as {@link Document}
     */
    public Document getXMLDocument(String url) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            return db.parse(new URL(url).openStream());
        } catch (ParserConfigurationException | IOException | SAXException e) {
            throw new RuntimeException(String.format("Error during creating xml document for url %s", url), e);
        }
    }

    /**
     * Method return
     *
     * @param positionNode - node 'pozycja' {@link Node}
     * @param rateType     {@link RateType}
     * @return rate ({@code double})
     */
    private double getRateFromPosition(Node positionNode, RateType rateType) {
        if (!Optional.ofNullable(positionNode).isPresent()) {
            throw new RuntimeException(String.format("Error during getting %s from Tag 'Pozycja'", rateType.tagName));
        }

        Optional<Node> any = IntStream.range(0, positionNode.getChildNodes().getLength())
                .mapToObj(positionNode.getChildNodes()::item).filter(node -> node.getNodeName().equals(rateType.tagName)).findAny();



        try {
            return nf.parse(any.get().getTextContent()).doubleValue();
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @param doc
     * @param currencySymbol
     * @return
     */
    private Node getPositionNodeFromCurrency(Document doc, String currencySymbol) {
        Optional<NodeList> currencyOccurrence = Optional.ofNullable(doc.getElementsByTagName(KOD_WALUTY_TAG));

        if (!currencyOccurrence.isPresent()) {
            throw new RuntimeException(String.format("Error during getting %s from Tag 'kod_waluty'", currencySymbol));
        }
        return IntStream.range(0, currencyOccurrence.get().getLength())
                .mapToObj(currencyOccurrence.get()::item)
                .filter(node -> node.getTextContent().equals(currencySymbol))
                .map(Node::getParentNode)
                .findFirst()
                .orElseThrow(() -> new RuntimeException(String.format("Error during parsing tag %s with first parameter = %s", KOD_WALUTY_TAG, currencySymbol)));
    }

    /**
     * Method get publication date from xml header file from tag PUBLICATION_DATE_TAG
     *
     * @param doc document {@link Document}
     * @return publication date {@link LocalDate}
     */
    public LocalDate getPublicationDate(Document doc) {
        Optional<NodeList> publicationDateOccurrence = Optional.ofNullable(doc.getElementsByTagName(PUBLICATION_DATE_TAG));

        if (!publicationDateOccurrence.isPresent()) {
            throw new RuntimeException(String.format("Error during getting %s from Tag 'kod_waluty'", publicationDateOccurrence));
        }

        return IntStream.range(0, publicationDateOccurrence.get().getLength())
                .mapToObj(publicationDateOccurrence.get()::item)
                .map(node -> node.getFirstChild().getNodeValue())
                .map(LocalDate::parse)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Error during parsing tag <data_publikacji>"));
    }


    /**
     * input parameter validation
     * <UL>
     * <LI>
     * validate if parameters array is not null is not contains nulls and is nor contains empty elements
     * </LI>
     * <LI>
     * checking correct number of parameters
     * </LI>
     * <LI>
     * checking first parameter, using currency three letter  pattern "\\b[a-zA-Z]{3}\\b"
     * </LI>
     * <LI>
     * checking if string place on position 2 and 3 can be parsed as date. @throws DateTimeParseException if the text cannot be parsed as date
     * </LI>
     * <LI>
     * checking start date if before and date.
     * </LI>
     * </UL>
     *
     * @param args - run parameters
     * @throws RuntimeException exception {@link RuntimeException}
     */
    private void validateParameters(String[] args) {


        //validate null
        assertThat(args)
                .as("checking run parameters")
                .overridingErrorMessage("Run parameters are null")
                .isNotNull().isNotEmpty().doesNotContainNull().doesNotContain("");

        //validate param qty
        assertThat(args).as("counting parameters")
                .overridingErrorMessage("has non enough parameters, current parameters array size = %s", args.length)
                .hasSize(ACCEPTED_NUMBER_OF_PARAMETERS);

        //validating first param
        assertThat(args[FIRST_PARAM])
                .as("Currency symbol")
                .overridingErrorMessage("First parameter should contain only 3 Letters, current param = %s", args[1])
                .matches(THREE_LETTER_CURRENCY_SYMBOL);

        //validating parsing dates param
        LocalDate startDate = LocalDate.parse(args[SECOND_PARAM]);
        LocalDate endDate = LocalDate.parse(args[THIRD_PARAM]);

        //validating date order
        assertThat(startDate)
                .as("Start date, end date rule")
                .overridingErrorMessage("Start date is not before end date, start date =%s , end date = %s", startDate, endDate)
                .isLessThan(endDate);
    }

}
