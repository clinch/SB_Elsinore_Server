package com.sb.elsinore.recipes;

import java.io.File;
import java.util.ArrayList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathException;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import ca.strangebrew.recipe.Fermentable;
import ca.strangebrew.recipe.Hop;
import ca.strangebrew.recipe.Quantity;
import ca.strangebrew.recipe.Recipe;
import ca.strangebrew.recipe.Style;
import ca.strangebrew.recipe.Yeast;

import com.sb.elsinore.BrewServer;
import com.sb.elsinore.LaunchControl;

/**
 * Read in a BeerXML file and provide a UI form to the user.
 * @author Doug Edey
 */
public class BeerXMLReader {

    /**
     * A static reference to the beerXML Instance.
     */
    private static BeerXMLReader instance = null;
    /**
     * The Document object that has been read in.
     **/
    private Document recipeDocument = null;

    /**
     * Get the singleton instance of this reader.
     * @return The current BeerXMLReader instance.
     */
    public static final BeerXMLReader getInstance() {
        if (BeerXMLReader.instance == null) {
            BeerXMLReader.instance = new BeerXMLReader();
        }

        return BeerXMLReader.instance;
    }

    /**
     * Set the file to read, and read it.
     * @param inputFile The file to read in.
     * @return True if file is read OK.
     */
    public final boolean readFile(final File inputFile) {
        // Assume that it's a valid file.

        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = null;
        try {
            dBuilder = dbFactory.newDocumentBuilder();
        } catch (ParserConfigurationException e1) {
            BrewServer.LOG.warning("Couldn't create Doc Builder");
            return false;
        }

        try {
            recipeDocument = dBuilder.parse(inputFile);
        } catch (Exception e) {
            String output = String.format(
                "Couldn't read beerXML File at: {0}",
                inputFile.getAbsolutePath());
            BrewServer.LOG.warning(output);
            LaunchControl.setMessage(output);
            return false;
        }
        return true;
    }

    public final ArrayList<String> getListOfRecipes() {
        ArrayList<String> nameList = new ArrayList<String>();
        XPath xp = null;
        try {
            xp = XPathFactory.newInstance().newXPath();
            NodeList recipeList =
                (NodeList) xp.evaluate(
                    "/RECIPES/RECIPE", recipeDocument, XPathConstants.NODESET);
            if (recipeList.getLength() == 0) {
                LaunchControl.setMessage("No Recipes found in file");
                return null;
            }

            for (int i = 0; i < recipeList.getLength(); i++) {
                Node recipeNode = recipeList.item(i);
                String recipeName = (String) xp.evaluate("NAME/text()",
                        recipeNode, XPathConstants.STRING);
                nameList.add(recipeName);
            }
        } catch (XPathException xpe) {
            BrewServer.LOG.warning("Couldn't run XPATH: " + xpe.getMessage());
            return null;
        }
        return nameList;
    }

    private void readRecipe(Document beerDocument, String name) throws XPathException {
        String recipeSelector = "";

        if (name != null) {
            recipeSelector = "[NAME[text()=\"" + name + "\"]]";
        }

        XPath xp = XPathFactory.newInstance().newXPath();
        NodeList recipeData =
                (NodeList) xp.evaluate(
                        "/RECIPES/RECIPE" + recipeSelector,
                        beerDocument, XPathConstants.NODESET);

        for (int i = 0; i < recipeData.getLength(); i++) {
            try {
                readSingleRecipe(recipeData.item(i));
            } catch (XPathException xpe) {
                BrewServer.LOG.warning("Couldn't read the recipe at index "
                        + i + " - " + xpe.getMessage());
            } catch (NumberFormatException nfe) {
                BrewServer.LOG.warning("Couldn't read the recipe at index "
                        + i + " due to a bad number " + nfe.getMessage());
            }
        }
    }

    private Recipe readSingleRecipe(Node recipeNode) throws XPathException, NumberFormatException {
        XPath xp = XPathFactory.newInstance().newXPath();
        Recipe recipe = new Recipe();

        // otherwise get the details from the recipe
        String recipeName = (String) xp.evaluate("NAME/text()", recipeNode, XPathConstants.STRING);
        String brewerName = (String) xp.evaluate("BREWER/text()", recipeNode, XPathConstants.STRING);
        String notes = (String) xp.evaluate("NOTES/text()", recipeNode, XPathConstants.STRING);

        double efficiency = getDouble(recipeNode, "EFFICIENCY", xp);
        double batchSize = getDouble(recipeNode, "BATCH_SIZE", xp);
        double boilSize = getDouble(recipeNode, "BOIL_SIZE", xp);
        int boilTime = getInteger(recipeNode, "BOIL_TIME", xp);

        recipe.setName(recipeName);
        recipe.setBrewer(brewerName);
        recipe.setPostBoil(new Quantity("litres", batchSize));
        recipe.setPreBoil(new Quantity("litres", boilSize));
        recipe.setBoilMinutes(boilTime);
        recipe.setEfficiency(efficiency);

        NodeList hopsList = (NodeList) xp.evaluate("HOPS",
                recipeNode, XPathConstants.NODESET);
        parseHops(recipe, hopsList);
        NodeList maltList = (NodeList) xp.evaluate("FERMENTABLES",
                recipeNode, XPathConstants.NODESET);
        parseMalts(recipe, maltList);
        NodeList yeastList = (NodeList) xp.evaluate("YEASTS",
                recipeNode, XPathConstants.NODESET);
        parseYeasts(recipe, yeastList);
        Node styleList = (Node) xp.evaluate("STYLE",
                recipeNode, XPathConstants.NODE);
        parseStyle(recipe, styleList);

        return recipe;
    }

    /**
     * Read in the hops.
     * @param recipe The Recipe being used.
     * @param hops The Hops NodeList
     * @throws XPathException If there's an XPAth issue.
     * @throws NumberFormatException if there's a bad number
     */
    private void parseHops(Recipe recipe, NodeList hops)
            throws XPathException, NumberFormatException {
        if (hops == null || hops.getLength() == 0) {
            return;
        }
        XPath xp = XPathFactory.newInstance().newXPath();
        NodeList hopList = (NodeList) xp.evaluate("HOP", hops.item(0), XPathConstants.NODESET);

        for (int i = 0; i < hopList.getLength(); i++) {
            Node hop = hopList.item(i);

            // Get the values
            String name = (String) xp.evaluate("NAME",
                    hop, XPathConstants.STRING);
            String temp = (String) xp.evaluate("AMOUNT",
                    hop, XPathConstants.STRING);
            double amount = Double.parseDouble(temp);
            temp = (String) xp.evaluate("ALPHA", hop, XPathConstants.STRING);
            double alpha = Double.parseDouble(temp);

            temp = (String) xp.evaluate("TIME", hop, XPathConstants.STRING);
            int time = (int) Math.round(Double.parseDouble(temp));
            String use = (String) xp.evaluate("USE",
                    hop, XPathConstants.STRING);

            Hop hopObject = new Hop();
            hopObject.setName(name);
            hopObject.setAlpha(alpha);
            hopObject.setAmountAs(amount, Quantity.KG);

            // Not all of these are used by beerxml 1.0
            if (use.equalsIgnoreCase("boil") || use.equalsIgnoreCase("aroma")
                    || use.equalsIgnoreCase("whirlpool")) {
                hopObject.setAdd(Hop.BOIL);
                hopObject.setMinutes(time);
            } else if (use.equalsIgnoreCase("dry hop")) {
                hopObject.setAdd(Hop.DRY);
                hopObject.setMinutes(time);
            } else if (use.equalsIgnoreCase("mash")) {
                hopObject.setAdd(Hop.MASH);
                hopObject.setMinutes(time);
            } else if (use.equalsIgnoreCase("first wort")) {
                hopObject.setAdd(Hop.FWH);
                hopObject.setMinutes(time);
            }

            // Everything is OK here, so add it in.
            recipe.addHop(hopObject);
        }
    }

    /**
     * Get the list of malts into the recipe object.
     * @param recipe The recipe to add the malts to
     * @param malts The NodeList of the malts.
     * @throws XPathException If there's an XPath issue
     * @throws NumberFormatException If there's a bad number.
     */
    private void parseMalts(Recipe recipe, NodeList malts)
            throws XPathException, NumberFormatException {
        if (malts == null || malts.getLength() == 0) {
            return;
        }

        XPath xp = XPathFactory.newInstance().newXPath();
        NodeList fermentableList = (NodeList) xp.evaluate("FERMENTABLE",
                malts.item(0), XPathConstants.NODESET);

        for (int i = 0; i < fermentableList.getLength(); i++) {
            try {
                Node fermentable = fermentableList.item(i);

                // Get the values
                String name = (String) xp.evaluate("NAME",
                        fermentable, XPathConstants.STRING);
                String type = (String) xp.evaluate("TYPE",
                        fermentable, XPathConstants.STRING);
                type = type.toLowerCase();
                boolean mashed = type.contains("malt")
                        || type.contains("grain");

                double amount = getDouble(fermentable, "AMOUNT", xp);
                double color = getDouble(fermentable, "COLOR", xp);
                double yield = getDouble(fermentable, "YIELD", xp);


                Fermentable malt = new Fermentable();
                malt.setName(name);
                malt.setPppg(yield);
                malt.setLov(color);
                malt.setMashed(mashed);
                malt.setAmount(amount);
                malt.setUnits(Quantity.KG);

                recipe.addMalt(malt);
            } catch (NumberFormatException nfe) {
                BrewServer.LOG.warning("Couldn't parse a number: "
                        + nfe.getMessage());
            } catch (Exception e) {
                if (e instanceof XPathException) {
                    throw (XPathException) e;
                } else {
                    BrewServer.LOG.warning(
                        "Couldn't read the weight for a malt" + e.getMessage());
                }
            }
        }
    }

    /**
     * Parse the yeasts.
     * @param recipe
     * @param yeasts
     * @throws XPathException
     * @throws NumberFormatException
     */
    private void parseYeasts(Recipe recipe, NodeList yeasts)
            throws XPathException, NumberFormatException {
        if (yeasts == null || yeasts.getLength() == 0) {
            return;
        }

        XPath xp = XPathFactory.newInstance().newXPath();
        NodeList yeastList = (NodeList) xp.evaluate("YEAST", yeasts.item(0), XPathConstants.NODESET);

        for (int i = 0; i < yeastList.getLength(); i++) {
            try {
                Node yeastItem = yeastList.item(i);

                String name = (String) xp.evaluate("NAME", yeastItem, XPathConstants.STRING);
                String type = (String) xp.evaluate("TYPE", yeastItem, XPathConstants.STRING);
                String form = (String) xp.evaluate("FORM", yeastItem, XPathConstants.STRING);
                String attenuation = (String) xp.evaluate("ATTENUATION", yeastItem, XPathConstants.STRING);

                Yeast yeast = new Yeast();
                yeast.setName(name);
                yeast.setForm(form);
                yeast.setType(type);
                yeast.setAttenuation(Double.parseDouble(attenuation));
                recipe.setYeast(yeast);
            } catch (NumberFormatException nfe) {
                BrewServer.LOG.warning("Couldn't parse a number: "
                        + nfe.getMessage());
            } catch (Exception e) {
                if (e instanceof XPathException) {
                    throw (XPathException) e;
                } else {
                    BrewServer.LOG.warning(e.getMessage());
                }
            }
        }
    }

    private void parseStyle(Recipe recipe, Node style) throws XPathExpressionException {
        if (style == null) {
            return;
        }

        XPath xp = XPathFactory.newInstance().newXPath();

        String name = (String) xp.evaluate("NAME", style, XPathConstants.STRING);
        String notes = (String) xp.evaluate("NOTES", style, XPathConstants.STRING);
        String categoryNumber = (String) xp.evaluate("CATEGORY_NUMBER", style, XPathConstants.STRING);
        String styleLetter = (String) xp.evaluate("STYLE_LETTER", style, XPathConstants.STRING);
        String styleGuide = (String) xp.evaluate("STYLE_GUIDE", style, XPathConstants.STRING);
        String type = (String) xp.evaluate("TYPE", style, XPathConstants.STRING);

        double ogMin = getDouble(style, "OG_MIN", xp);
        double ogMax = getDouble(style, "OG_MAX", xp);
        double fgMin = getDouble(style, "FG_MIN", xp);
        double fgMax = getDouble(style, "FG_MAX", xp);
        double ibuMin = getDouble(style, "IBU_MIN", xp);
        double ibuMax = getDouble(style, "IBU_MAX", xp);
        double colorMin = getDouble(style, "COLOR_MIN", xp);
        double colorMax = getDouble(style, "COLOR_MAX", xp);
        double abvMin = getDouble(style, "ABV_MIN", xp);
        double abvMax = getDouble(style, "ABV_MAX", xp);

        // Check to see if we have this style
        Style beerStyle = new Style();
        beerStyle.setName(name);
        beerStyle.setCategory(styleLetter);
        beerStyle.setCatNum(categoryNumber);
        beerStyle.setYear(styleGuide);
        beerStyle.setComments(notes);
        beerStyle.setType(type);
        beerStyle.setAlcHigh(abvMax);
        beerStyle.setAlcLow(abvMin);
        beerStyle.setFgHigh(fgMax);
        beerStyle.setFgLow(fgMin);
        beerStyle.setOgHigh(ogMax);
        beerStyle.setOgLow(ogMin);
        beerStyle.setSrmHigh(colorMax);
        beerStyle.setSrmLow(colorMin);
        beerStyle.setIbuHigh(ibuMax);
        beerStyle.setIbuLow(ibuMin);
        recipe.setStyle(beerStyle);
    }

    private double getDouble(NodeList element, String name, XPath xp) {
        try {
            String temp = (String) xp.evaluate(name.toUpperCase(), element, XPathConstants.STRING);
            return Double.parseDouble(temp);
        } catch (XPathException xpe) {
            return 0.0;
        } catch (NumberFormatException nfe) {
            nfe.printStackTrace();
            return 0.0;
        }
    }

    private double getDouble(Node element, String name, XPath xp) {
        try {
            String temp = (String) xp.evaluate(name.toUpperCase(), element, XPathConstants.STRING);
            return Double.parseDouble(temp);
        } catch (XPathException xpe) {
            xpe.printStackTrace();
            return 0.0;
        } catch (NumberFormatException nfe) {
            nfe.printStackTrace();
            return 0.0;
        }
    }

    private int getInteger(Node element, String name, XPath xp) {
        try {
            String temp = (String) xp.evaluate(name.toUpperCase(), element, XPathConstants.STRING);
            return Integer.parseInt(temp);
        } catch (XPathException xpe) {
            xpe.printStackTrace();
            return 0;
        } catch (NumberFormatException nfe) {
            nfe.printStackTrace();
            return 0;
        }
    }
}