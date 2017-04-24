package pl.parser.nbp;

/**
 * Created by pwl on 2017-04-21.
 */
public enum RateType {

    SELL_RATE("kurs_sprzedazy"),
    BUY_RATE("kurs_kupna");

    public String tagName;

    RateType(String tagName) {
        this.tagName = tagName;
    }
}
