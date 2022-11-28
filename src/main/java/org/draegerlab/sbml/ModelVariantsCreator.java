/**
 *
 */
package org.draegerlab.sbml;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import javax.xml.stream.XMLStreamException;

import org.apache.log4j.Logger;
import org.sbml.jsbml.Model;
import org.sbml.jsbml.Reaction;
import org.sbml.jsbml.SBMLDocument;
import org.sbml.jsbml.SBMLReader;
import org.sbml.jsbml.TidySBMLWriter;
import org.sbml.jsbml.ext.fbc.FBCConstants;
import org.sbml.jsbml.ext.fbc.FBCReactionPlugin;

/**
 * @author Andreas Dr&auml;ger
 *
 */
public class ModelVariantsCreator {

  private static transient Logger logger = Logger.getLogger(ModelVariantsCreator.class);

  /**
   * Changes means reaction ID, reversible, lower bound.
   *
   * @param args Original Model file, CSV file with changes, outfile, optional separator in CSV
   *
   * @throws IOException
   * @throws XMLStreamException
   */
  public static void main(String[] args) throws XMLStreamException, IOException {
    new ModelVariantsCreator(new File(args[0]), new File(args[1]), new File(args[2]), args.length > 3 ? args[3] : ";");
  }

  /**
   * @param args
   * @throws XMLStreamException
   * @throws IOException
   * @throws FileNotFoundException
   */
  public ModelVariantsCreator(File model, File csv, File out, String separator)
      throws XMLStreamException, IOException, FileNotFoundException {
    SBMLDocument doc = SBMLReader.read(model);
    Model m = doc.getModel();

    BufferedReader bf = new BufferedReader(new FileReader(csv));
    String line;
    int row = 0;
    while ((line = bf.readLine()) != null) {
      if (row > 0) {
        processCurrentRow(line, separator, m);
        logger.info(row);
      }
      row++;
    }
    bf.close();

    TidySBMLWriter.write(doc, out, ' ', (short) 2);
  }

  private void processCurrentRow(String line, String separator, Model m) {
    String columns[] = line.split(separator);
    Reaction r = m.getReaction(columns[0]);
    r.setReversible(Boolean.parseBoolean(columns[1]));
    FBCReactionPlugin rplug = (FBCReactionPlugin) r.getPlugin(FBCConstants.shortLabel);
    rplug.setLowerFluxBound(columns[2]);
    logger.info(line);
  }

}
