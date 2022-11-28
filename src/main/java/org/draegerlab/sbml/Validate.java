package org.draegerlab.sbml;

import java.io.File;
import java.io.IOException;

import javax.xml.stream.XMLStreamException;

import org.sbml.jsbml.SBMLDocument;
import org.sbml.jsbml.SBMLError;
import org.sbml.jsbml.SBMLErrorLog;
import org.sbml.jsbml.SBMLReader;

/**
 * This class reads an SBML file and validates it using JSBML.
 *
 * @author Andreas Dr&auml;ger
 */
public class Validate {

  public static void main(String[] args) throws XMLStreamException, IOException {
    SBMLDocument doc = SBMLReader.read(new File(args[0]));
    doc.checkConsistencyOffline();
    SBMLErrorLog errors = doc.getListOfErrors();
    for (int i = 0; i < errors.getNumErrors(); i++) {
      SBMLError e = errors.getError(i);
      if (!e.isWarning()) {
        System.out.println(e);
      }
    }
  }
}
