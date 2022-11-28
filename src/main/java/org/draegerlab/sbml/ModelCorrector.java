/**
 *
 */
package org.draegerlab.sbml;

import java.beans.PropertyChangeEvent;
import java.io.File;
import java.io.IOException;

import javax.swing.tree.TreeNode;
import javax.xml.stream.XMLStreamException;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.sbml.jsbml.CVTerm;
import org.sbml.jsbml.CVTerm.Qualifier;
import org.sbml.jsbml.Compartment;
import org.sbml.jsbml.Model;
import org.sbml.jsbml.Parameter;
import org.sbml.jsbml.SBMLDocument;
import org.sbml.jsbml.SBMLReader;
import org.sbml.jsbml.Species;
import org.sbml.jsbml.TidySBMLWriter;
import org.sbml.jsbml.Unit;
import org.sbml.jsbml.Unit.Kind;
import org.sbml.jsbml.util.ModelBuilder;
import org.sbml.jsbml.util.TreeNodeChangeListener;
import org.sbml.jsbml.util.TreeNodeRemovedEvent;

/**
 * Performs smaller corrections to an SBML file to pass validation.
 *
 * @author Andreas Dr&auml;ger
 *
 */
public class ModelCorrector implements TreeNodeChangeListener {

  public static final String UNIT_MMOL_PER_GRAM_DW_PER_HOUR = "mmol_per_gDW_per_hr";
  public static final String UNIT_FEMTO_LITRE = "fL";
  public static final String UNIT_MMOL_PER_GRAM_DW = "mmol_per_gDW";
  private static final transient Logger logger = Logger.getLogger(ModelCorrector.class);
  private boolean changed = false;
  private SBMLDocument doc;

  public static void correct(SBMLDocument doc) {
    new ModelCorrector(doc);
  }

  public ModelCorrector(SBMLDocument doc) {
    this.doc = doc;
    this.doc.addTreeNodeChangeListener(this, true);
    Model m = doc.getModel();
    // Create default units
    ModelBuilder mb = new ModelBuilder(doc);
    if (!m.containsUnitDefinition("hour") && !m.containsUnitDefinition("h")) {
      Unit h = new Unit(3600d, 0, Kind.SECOND, 1d, doc.getLevel(), doc.getVersion());
      h.setMetaId("meta_hour");
      h.addCVTerm(new CVTerm(Qualifier.BQB_IS, "https://identifiers.org/UO:0000032"));
      mb.buildUnitDefinition("h", "hour", h);
    }
    if (!m.isSetTimeUnits()) {
      m.setTimeUnits(m.containsUnitDefinition("h") ? "h" : "hour");
    }
    if (!m.containsUnitDefinition(UNIT_FEMTO_LITRE)) {
      Unit fL = new Unit(1d, -3, Kind.LITRE, 1d, doc.getLevel(), doc.getVersion());
      fL.setMetaId("meta_fL");
      fL.addCVTerm(new CVTerm(Qualifier.BQB_IS, "https://identifiers.org/UO:0000104"));
      mb.buildUnitDefinition(UNIT_FEMTO_LITRE, "femto litres", fL);
    }
    if (!m.isSetVolumeUnits()) {
      m.setVolumeUnits(UNIT_FEMTO_LITRE);
    }
    if (!m.containsUnitDefinition(UNIT_MMOL_PER_GRAM_DW)) {
      Unit mole = new Unit(1d, -3, Kind.MOLE, 1d, doc.getLevel(), doc.getVersion());
      Unit gram = new Unit(1d, 0, Kind.GRAM, -1d, doc.getLevel(), doc.getVersion());
      //TODO: annotation.
      mb.buildUnitDefinition(UNIT_MMOL_PER_GRAM_DW, "millimoles per gram dry weight", mole, gram);
    }
    if (!m.isSetSubstanceUnits()) {
      m.setSubstanceUnits(UNIT_MMOL_PER_GRAM_DW);
    }
    if (!m.isSetExtentUnits()) {
      m.setExtentUnits(UNIT_MMOL_PER_GRAM_DW);
    }
    if (!m.containsUnitDefinition(UNIT_MMOL_PER_GRAM_DW_PER_HOUR)) {
      Unit mole = new Unit(1d, -3, Kind.MOLE, 1d, doc.getLevel(), doc.getVersion());
      Unit gram = new Unit(1d, 0, Kind.GRAM, -1d, doc.getLevel(), doc.getVersion());
      Unit hour = new Unit(3600d, 0, Kind.SECOND, -1d, doc.getLevel(), doc.getVersion());
      //TODO: annotation.
      mb.buildUnitDefinition(UNIT_MMOL_PER_GRAM_DW_PER_HOUR, "millimoles per gram dry weight per hour", mole, gram, hour);
    }
    // Initialize compadrtment sizes
    for (Compartment c : m.getListOfCompartments()) {
      if (!c.isSetSize()) {
        c.setSize(Double.NaN);
        logger.log(Level.DEBUG, c.getId() + ": set size to NaN");
      }
      if (!c.isSetSpatialDimensions()) {
        c.setSpatialDimensions(3d);
        logger.log(Level.DEBUG, c.getId() + ": set 3 as spatial dimensions");
      }
    }
    // Initialize species concentrations
    for (Species s : m.getListOfSpecies()) {
      if (!s.isSetValue()) {
        s.setInitialAmount(Double.NaN);
        logger.log(Level.DEBUG, s.getId());
      }
    }
    // Fix parameter units to default to extent units (for constraint-based modeling)
    for (Parameter p : m.getListOfParameters()) {
      if (!p.isSetUnits()) {
        p.setUnits(m.getExtentUnits());
        logger.log(Level.DEBUG, p.getId() + ": set extent units");
      }
    }
  }

  /**
   * @param args Path to two files: original model and output.
   * @throws IOException
   * @throws XMLStreamException
   */
  public static void main(String[] args) throws XMLStreamException, IOException {
    ModelCorrector mc = new ModelCorrector(SBMLReader.read(new File(args[0])));
    if (mc.isChanged()) {
      TidySBMLWriter.write(mc.getSBMLDocument(), new File(args[1]), ' ', (short) 2);
    }
  }

  /**
   *
   * @return The SBML document that this corrector instance is working on.
   */
  public SBMLDocument getSBMLDocument() {
    return doc;
  }

  /**
   *
   * @return {@code true} if the model has been changed.
   */
  public boolean isChanged() {
    return changed;
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt) {
    changed = true;
  }

  @Override
  public void nodeAdded(TreeNode node) {
    changed = true;
  }

  @Override
  public void nodeRemoved(TreeNodeRemovedEvent event) {
    changed = true;
  }

}
