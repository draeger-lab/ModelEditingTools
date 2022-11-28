package org.draegerlab.sbml;

import java.io.File;
import java.io.IOException;

import javax.xml.stream.XMLStreamException;

import org.sbml.jsbml.ListOf;
import org.sbml.jsbml.Model;
import org.sbml.jsbml.Reaction;
import org.sbml.jsbml.SBMLDocument;
import org.sbml.jsbml.SBMLError;
import org.sbml.jsbml.SBMLError.SEVERITY;
import org.sbml.jsbml.SBMLReader;
import org.sbml.jsbml.SimpleSpeciesReference;
import org.sbml.jsbml.SpeciesReference;
import org.sbml.jsbml.TidySBMLWriter;
import org.sbml.jsbml.ext.layout.Layout;
import org.sbml.jsbml.ext.layout.LayoutConstants;
import org.sbml.jsbml.ext.layout.LayoutModelPlugin;
import org.sbml.jsbml.ext.layout.ReactionGlyph;
import org.sbml.jsbml.ext.layout.SpeciesGlyph;
import org.sbml.jsbml.ext.layout.SpeciesReferenceGlyph;

public class FixLayoutInternalIDs {

  public static void main(String[] args) throws XMLStreamException, IOException {
    SBMLDocument doc = SBMLReader.read(new File(args[0]));
    Model m = doc.getModel();
    LayoutModelPlugin layoutPlug = (LayoutModelPlugin) doc.getModel().getPlugin(LayoutConstants.layout);

    for (Layout layout : layoutPlug.getListOfLayouts()) {
      if (layout.isSetListOfReactionGlyphs()) {
        for (ReactionGlyph rg : layout.getListOfReactionGlyphs()) {
          if (rg.isSetListOfSpeciesReferenceGlyphs()) {
            for (SpeciesReferenceGlyph srg : rg.getListOfSpeciesReferenceGlyphs()) {
              if (srg.isSetSpeciesGlyph()) {
                SpeciesGlyph sg = srg.getSpeciesGlyphInstance();
                if (rg.isSetReaction()) {
                  Reaction r = (Reaction) rg.getReactionInstance();
                  if (r != null) {
                    SpeciesReference sr = findSpeciesReference(r, sg.getSpecies());
                    if (sr != null) {
                      if (sr.isSetId() && !sr.getId().equals(srg.getSpeciesReference())) {
                        srg.setSpeciesReference(sr);
                      } else {
                        sr.setId(srg.getSpeciesReference());
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }

    File out = new File(args[1]);
    TidySBMLWriter.write(doc, out, ' ', (short) 2);
    doc.checkConsistencyOffline();
    for (SBMLError e : doc.getListOfErrors().getErrorsBySeverity(SEVERITY.ERROR)) {
      System.out.println(e.getMessage());
    }
  }

  /**
   * Searches in all three lists of species references within the given reaction,
   * i.e., reactants, products, and modifiers, and returns the first such reference
   * to the given species identifier.
   *
   * @param r
   * @param species
   * @return The first reference with the given species identifiers within the
   *   lists of reactants, products, and modifiers with the given identifier or
   *   {@code null} if none exists.
   */
  private static SpeciesReference findSpeciesReference(Reaction r,
    String species) {
    SpeciesReference sr;
    if (r.isSetListOfReactants()) {
      sr = findSpeciesReference(r.getListOfReactants(), species);
      if (sr != null) {
        return sr;
      }
    }
    if (r.isSetListOfProducts()) {
      sr = findSpeciesReference(r.getListOfProducts(), species);
      if (sr != null) {
        return sr;
      }
    }
    if (r.isSetListOfModifiers()) {
      return findSpeciesReference(r.getListOfModifiers(), species);
    }
    return null;
  }

  /**
   * Searches a reference object within the given list to the species with the
   * given identifier and returns it, {@code null} otherwise.
   * @param <E>
   * @param listOfReferences
   * @param species
   * @return
   */
  @SuppressWarnings("unchecked")
  private static <E extends SimpleSpeciesReference> E findSpeciesReference(
    ListOf<? extends SimpleSpeciesReference> listOfReferences, String species) {
    for (SimpleSpeciesReference ssr : listOfReferences) {
      if (ssr.getSpecies().equals(species)) {
        return (E) ssr;
      }
    }
    return null;
  }
}
