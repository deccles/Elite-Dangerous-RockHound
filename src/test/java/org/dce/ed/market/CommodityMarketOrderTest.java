package org.dce.ed.market;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CommodityMarketOrderTest {
    @Test
    void loadsMarketCategoriesAndSortsUnknownCommoditiesLast(@TempDir Path directory)
            throws Exception {
        Path market = directory.resolve("Market.json");
        Files.writeString(market, """
                {"Items":[
                  {"Name":"$mineraloil_name;","Name_Localised":"Mineral Oil",
                   "Category":"$MARKET_category_chemicals;"},
                  {"Name":"$agronomictreatment_name;","Name_Localised":"Agronomic Treatment",
                   "Category":"$MARKET_category_chemicals;"},
                  {"Name":"$foodcartridges_name;","Name_Localised":"Food Cartridges",
                   "Category":"$MARKET_category_foods;"},
                  {"Name":"$waterpurifiers_name;","Name_Localised":"Water Purifiers",
                   "Category":"$MARKET_category_industrial_materials;"},
                  {"Name":"$beer_name;","Name_Localised":"Beer",
                   "Category":"$MARKET_category_drugs;"}
                ]}
                """);
        CommodityMarketOrder order = CommodityMarketOrder.load(market);
        List<String> commodities = new ArrayList<>(List.of(
                "Unknown Commodity", "Beer", "Water Purifiers", "Mineral Oil",
                "Food Cartridges", "Agronomic Treatment"));

        commodities.sort(order.comparator());

        assertEquals(List.of("Agronomic Treatment", "Mineral Oil", "Food Cartridges",
                "Water Purifiers", "Beer", "Unknown Commodity"), commodities);
    }
}
