package com.patrickma.magiccircles;

import net.minecraft.util.RandomSource;

import java.util.List;

/**
 * What a Fairy Queen is called. Every queen carries a name and the title after it - "Zuzo, Queen
 * of the Faye" - given the moment she is crowned (see {@code FairyCourt#crown} and {@code
 * entity/FairyQueenEntity#finalizeSpawn}): a fairy who already had a name keeps it and gains the
 * title; any other draws one from this list. Zuzo, who led the Faye into Yllumere (see the Book of
 * the Faye), heads it.
 */
public final class FairyQueenNames
{
    public static final String TITLE = ", Queen of the Faye";

    private static final List<String> NAMES = List.of(
            "Zuzo", "Aelindra", "Thessaly", "Nimueh", "Oriel", "Sylvaine", "Ismerie", "Calanthe", "Yseult", "Vaelora",
            "Elowen", "Maeve", "Titania", "Aurelith", "Selenwyn", "Isolde", "Lirael", "Naeris", "Fionnuala", "Elarinya",
            "Ysabeau", "Morwenna", "Aveline", "Celestine", "Idrienne", "Rhoswen", "Seraphel", "Liadan", "Ondine", "Vesperine",
            "Amarantha", "Eirlys", "Nerissa", "Ithilwen", "Caelia", "Meliora", "Solenne", "Evanthe", "Rosalind", "Thalassa",
            "Ilyrra", "Brisbane", "Aeriel", "Nymeria", "Valeska", "Fenella", "Oleandra", "Wrenna", "Ysolde", "Eluned",
            "Anwyn", "Melisande", "Tamsin", "Yavanna", "Perenelle", "Glimmera", "Ninniane", "Ariadne", "Elspeth", "Corisande",
            "Sionnach", "Delphine", "Auriane", "Isaura", "Merewyn", "Lunetta", "Saoirse", "Vivenne", "Aislinn", "Belphoebe",
            "Marisel", "Ceridwen", "Elphaba", "Ilaria", "Nocturne", "Fayelle", "Sorcha", "Andraste", "Rowenna", "Zephyrine",
            "Yllaria", "Oonagh", "Serilda", "Amaranthe", "Maelis", "Thistlewyn", "Evadne", "Linnea", "Esmerine", "Cassiel",
            "Nivienne", "Orlaith", "Wisteria", "Tanaquil", "Elaraine", "Sabrielle", "Ianthe", "Melusine", "Eirian", "Lumielle");

    private FairyQueenNames()
    {
    }

    public static String pick(RandomSource random)
    {
        return NAMES.get(random.nextInt(NAMES.size()));
    }

    /** "Zuzo" becomes "Zuzo, Queen of the Faye" - and a name that already carries the title is left alone. */
    public static String crown(String name)
    {
        return name.endsWith(TITLE) ? name : name + TITLE;
    }
}
