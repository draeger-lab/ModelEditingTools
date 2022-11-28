/**
 *
 */
package org.draegerlab.sbml;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;

import javax.xml.stream.XMLStreamException;

import org.sbml.jsbml.CVTerm.Qualifier;
import org.sbml.jsbml.Compartment;
import org.sbml.jsbml.Model;
import org.sbml.jsbml.SBMLDocument;
import org.sbml.jsbml.SBMLReader;
import org.sbml.jsbml.Species;

/**
 * Pulls a list of Species identifiers (KEGG IDs) from a model.
 *
 * @author Andreas Dr&auml;ger
 *
 */
public class ExtractSpeciesIDs {

  /**
   * @param args
   *   1) Path to a model file in SBML format
   *   2) Compartment ID
   *   3) Pattern for the resources
   * @throws IOException
   * @throws XMLStreamException
   */
  public static void main(String[] args) throws XMLStreamException, IOException {
    SBMLDocument doc = SBMLReader.read(new GZIPInputStream(new FileInputStream(new File(args[0]))));
    Model m = doc.getModel();
    Compartment c = m.getCompartment(args[1]);
    for (Species s : m.getListOfSpecies()) {
      if (s.isSetCompartment() && s.getCompartment().equals(c.getId())) {
        String filter = "";// args[2];
        System.out.printf("%s\t%s\n", s.getId(), s.filterCVTerms(Qualifier.BQB_IS, filter, true));
      }
    }
  }
}
