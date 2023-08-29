/**
 *
 */
package org.draegerlab.sbml;

import static java.text.MessageFormat.format;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import javax.xml.stream.XMLStreamException;
import javax.xml.transform.TransformerException;

import org.jdom2.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.sbml.jsbml.Model;
import org.sbml.jsbml.SBMLDocument;
import org.sbml.jsbml.SBMLError;
import org.sbml.jsbml.SBMLException;
import org.sbml.jsbml.SBMLReader;
import org.sbml.jsbml.TidySBMLWriter;
import org.sbml.jsbml.ext.comp.CompConstants;
import org.sbml.jsbml.ext.comp.CompModelPlugin;
import org.sbml.jsbml.ext.comp.CompSBMLDocumentPlugin;
import org.sbml.jsbml.ext.comp.Deletion;
import org.sbml.jsbml.ext.comp.ExternalModelDefinition;
import org.sbml.jsbml.ext.comp.Submodel;
import org.sbml.jsbml.ext.fbc.FBCConstants;
import org.sbml.jsbml.ext.fbc.FBCModelPlugin;
import org.sbml.jsbml.ext.fbc.GeneProduct;
import org.sbml.jsbml.util.SBMLtools;
import org.sbml.jsbml.util.SubModel;

import de.unirostock.sems.cbarchive.ArchiveEntry;
import de.unirostock.sems.cbarchive.CombineArchive;
import de.unirostock.sems.cbarchive.CombineArchiveException;
import de.unirostock.sems.cbarchive.meta.OmexMetaDataObject;
import de.unirostock.sems.cbarchive.meta.omex.OmexDescription;
import de.unirostock.sems.cbarchive.meta.omex.VCard;
import de.zbit.util.logging.LogUtil;

/**
 * Reads a model in SBML format and a file with reaction identifiers. It will
 * generate a hierarchical model from that containing only the reactions from
 * that list. The algorithm proceeds as follows:
 * <ol>
 * <li>Create a hierarchical SBML model from the reaction list, which completely
 * loads the base model
 * <li>Deletes all reactions that are not on the
 * respective list. Note: This does operate on the indices of the reactions, not
 * their identifier. There is a specific so-called deletion object for this
 * purpose.
 * </ol>
 * Then we have our cell type specific models in standardized formats.
 * With the so-called "flattening" function, JSBML can automatically
 * generate a CobraPy compatible model from each of these models.
 * This means that the models are already standardized.
 *
 * @author Andreas Dr&auml;ger
 */
public class TissueModelExtractor {
  
  private static final String COMP = CompConstants.shortLabel;

  private static final String SBML_EXTENSION = ".sbml";
  private static final String CSV_EXTENSION = ".csv";
  public static final String MACOSX_HIDDEN_FOLDER = "__MACOSX";
  public static final String OMEX_EXTENSION = ".omex";
  
  private static URI SBML_LEVEL_3_VERSION_1_RELEASE_2;

  /** A {@link Logger} for this class */
  private static final Logger logger = Logger.getLogger(TissueModelExtractor.class.getName());

  static {
    try {
      SBML_LEVEL_3_VERSION_1_RELEASE_2 = new URI("https://identifiers.org/combine.specifications/sbml.level-3.version-1.core.release-2");
    } catch (URISyntaxException exc) {
      exc.printStackTrace();
      System.exit(1);
    }
    LogUtil.initializeLogging("org");
  }


  /**
   * The buffer that is used to extract some files.
   */
  public static int BUFFER = 4096;

  /** The (consistent) base model */
  private final SBMLDocument baseDoc;
  /** Where to store the generated models */
  private final CombineArchive archive;
  /** Where to store the (temporary) SBML files */
  private final File targetDir;
  /** The file where the base document is stored */
  private final File baseDocFile;

  private final String md5;


  /**
   *
   * @param doc the base document from which all submodels will be derived.
   * @param ca the archive file that will bundle all files.
   * @param descriptor a meaningful text that describes the content of the base model. This descriptor will be used as the name of the base file and can, consequently, not contain any blanks. It will also be used as the name within the SBML models. For this purpose, all underscore symbols will be automatically replaced with blanks.
   * @param targetDir where to store the temporary SBML files.
   * @throws SBMLException
   * @throws IOException
   * @throws URISyntaxException
   * @throws XMLStreamException
   * @throws NoSuchAlgorithmException
   */
  public TissueModelExtractor(SBMLDocument doc, CombineArchive ca, String descriptor, File targetDir)
        throws SBMLException, IOException, URISyntaxException, XMLStreamException, NoSuchAlgorithmException {
    this.baseDoc = doc;
    this.archive = ca;
    this.targetDir = targetDir;
    this.baseDocFile = writeTemporaryModelFile(doc, descriptor, targetDir);
    this.md5 = checksum(baseDocFile);
    archive.setMainEntry(addSBMLasArchiveEntry(baseDocFile));
  }


  /**
   * @param args
   *        1) Input: The path to the SBML model that serves as the base model.
   *        2) Input: The path to a ZIP file containing lists of reaction identifiers to keep in tissue-specific models
   *        3) Output: The path to the target folder where the COMBINE archive is to be created as output.
   * @throws IOException
   * @throws XMLStreamException
   * @throws CombineArchiveException
   * @throws ParseException
   * @throws JDOMException
   * @throws URISyntaxException
   * @throws TransformerException
   * @throws NoSuchAlgorithmException
   * @throws SBMLException
   */
  public static void main(String[] args)
      throws XMLStreamException, IOException, JDOMException, ParseException,
      CombineArchiveException, URISyntaxException, TransformerException, SBMLException, NoSuchAlgorithmException {
    long time1 = System.currentTimeMillis();
    
    File baseModelFile = new File(args[0]);
    SBMLDocument baseModel = SBMLReader.read(baseModelFile);
    File zipFile = new File(args[1]);
    
    // Create a folder where to store all these models.
    // We simply reuse the name of the ZIP archive with CSV files for naming the .
    File targetFolder = new File(args[2]);
    String descriptor = nameWithoutExtension(zipFile);
    File outputFolder = new File(targetFolder.getAbsolutePath() + File.separatorChar + descriptor);
    if (!outputFolder.exists()) {
      outputFolder.mkdir();
    }
    
    File archiveFile = new File(targetFolder.getAbsolutePath() + File.separatorChar + descriptor + OMEX_EXTENSION);
    if (archiveFile.exists()) {
      archiveFile.delete();
    }
    
    CombineArchive combineArchive = new CombineArchive(archiveFile);
    
    TissueModelExtractor tme = new TissueModelExtractor(
      baseModel, combineArchive, nameWithoutExtension(baseModelFile), outputFolder);
    
    List<File> listOfModels = tme.buildSubModels(new ZipFile(zipFile));

    long time2 = System.currentTimeMillis();

    // Pack the archive
    tme.packArchive(listOfModels);

    long time3 = System.currentTimeMillis();
    logger.info(format("Time for creating models:\t{0,number} min\nTime for packing the archive:\t{1,number} min", (time2 - time1)/60000d, (time3 - time2)/60000d));
  }
  
  public static String nameWithoutExtension(@NotNull File file) {
    String fileName = file.getName();
    int lastDotIndex = fileName.lastIndexOf('.');
    return lastDotIndex > 0 ? fileName.substring(0, lastDotIndex) : fileName;
  }


  /**
   * @param listOfModels
   * @throws IOException
   * @throws URISyntaxException
   * @throws XMLStreamException
   * @throws TransformerException
   */
  public void packArchive(@org.jetbrains.annotations.NotNull List<File> listOfModels) throws IOException, URISyntaxException, XMLStreamException, TransformerException {
    addArchiveMetaData();
    for (int i = 0; i < listOfModels.size(); i++) {
      File sbml = listOfModels.get(i);
      addSBMLasArchiveEntry(sbml);
      logger.info(format("Adding file #{0,number,integer} to archive: {1}", i, sbml.getAbsolutePath()));
    }
    finalizeArchive();
  }


  /**
   *
   * @param zFile an archive containing CSV files, each with reaction indices to be used in submodels.
   * @return a list of model files each corresponding to one initial CSV file and, consequently, with only those reactions in it as specified by the reaction indices in the CSV file.
   * @throws ZipException
   * @throws IOException
   * @throws XMLStreamException
   */
  public List<File> buildSubModels(ZipFile zFile)
      throws ZipException, IOException, XMLStreamException {
    Enumeration<? extends ZipEntry> entries = zFile.entries();
    List<File> listOfModels = new ArrayList<File>();
    for (int i = 0, j = 0; entries.hasMoreElements(); i++) {
      ZipEntry entry = entries.nextElement();
      String entryName = entry.getName();
      if (!entry.isDirectory() && entryName.toLowerCase().endsWith(CSV_EXTENSION) && !entryName.startsWith(MACOSX_HIDDEN_FOLDER)) {
        if (entry.getSize() > 0) {
          logger.info(format("Processing model number {1,number,integer}:\t{0}", entryName, ++j));
          int[] rIdxs = parseReactionList(zFile.getInputStream(entry));
          logger.fine(format("Current file contains: {0}", Arrays.toString(rIdxs)));
          SBMLDocument subDoc = createTissueModelComp(rIdxs);
          String descriptor = entryName.substring(entryName.lastIndexOf('/') + 1);
          if (descriptor.endsWith(CSV_EXTENSION)) {
            descriptor = descriptor.substring(0, descriptor.lastIndexOf('.'));
          }
          listOfModels.add(writeTemporaryModelFile(subDoc, descriptor, targetDir));
        } else {
          logger.info(format("Skipping:\t{0}", entryName));
        }
      }
    }
    zFile.close();
    return listOfModels;
  }


  /**
   *
   * @param inputStream
   * @return
   * @throws IOException
   */
  public int[] parseReactionList(InputStream inputStream)
      throws IOException {
    String content[] = extractFile(inputStream).trim().split("\n");
    int rIdxs[] = new int[content.length];
    // Index shift from MATLAB to Java
    Arrays.parallelSetAll(rIdxs, i -> Integer.parseInt(content[i]) - 1);
    //    for (int j = 0; j < content.length; j++) {
    //      rIdxs[j] = Integer.parseInt(content[j]);
    //    }
    return rIdxs;
  }

  /**
   * Finalize the archive (write manifest and meta data) and close it.
   *
   * @throws IOException
   * @throws TransformerException
   */
  private void finalizeArchive() throws IOException, TransformerException {
    logger.info(format("Packing archive is done. Finalizing {0}", archive.getZipLocation().getAbsolutePath()));
    archive.pack(false);
    archive.close();
  }


  /**
   * TODO: This writes hard-coded information to the archive and needs to be updated!
   */
  private void addArchiveMetaData() {
    // Meta data
    List<VCard> creators = new ArrayList<VCard> ();
    creators.add (new VCard ("Dr\u00E4ger", "Andreas",
      "andreas.draeger@uni-tuebingen.de", "Eberhard Karl University of T\u00FCbingen"));
    creators.add (new VCard ("Leonidou", "Nantia",
      "nantia.leonidou@uni-tuebingen.de", "Eberhard Karl University of T\u00FCbingen"));
    creators.add (new VCard ("Renz", "Alina",
      "alina.renz@uni-tuebingen.de", "Eberhard Karl University of T\u00FCbingen"));
    archive.addDescription(new OmexMetaDataObject(new OmexDescription(creators, new Date())));
  }


  /**
   * @param sbmlFile
   * @return The generated archive entry.
   * @throws IOException
   * @throws URISyntaxException
   * @throws XMLStreamException
   * @throws SBMLException
   */
  private ArchiveEntry addSBMLasArchiveEntry(File sbmlFile) throws IOException, URISyntaxException, SBMLException, XMLStreamException {
    return archive.addEntry(sbmlFile.getParentFile(), sbmlFile, SBML_LEVEL_3_VERSION_1_RELEASE_2);
  }

  /**
   * @param subDoc Submodel in SBML format
   * @param descriptor Name of the temporary file (without extension) and also display name for the submodel itself. In either case blanks are created or avoided and swapped with underscores.
   * @param directory Where to store the temporary model file.
   * @return The temporary model file.
   * @throws IOException
   * @throws XMLStreamException
   */
  public File writeTemporaryModelFile(SBMLDocument subDoc, String descriptor, File directory)
      throws IOException, XMLStreamException {
    if (!subDoc.isSetName()) {
      subDoc.setName(convertToDisplayName(descriptor));
    }
    //subDoc.checkConsistencyOffline();
    for (int i = 0; i < subDoc.getErrorCount(); i++) {
      SBMLError error = subDoc.getError(i);
      if (!error.isWarning()) {
        logger.severe(error.getMessage());
      }
    }
    // create temporary SBML file (we add an underscore to separate the meaningful name from the auto-genrated random number)
    File tmp = File.createTempFile(descriptor + '_', SBML_EXTENSION, directory);
    TidySBMLWriter.write(subDoc, tmp, ' ', (short) 2);
    logger.info(format("File written: {0}", tmp.getAbsolutePath()));
    return tmp;
  }


  /**
   *
   * @param descriptor
   * @return
   */
  public String convertToDisplayName(String descriptor) {
    descriptor = descriptor.replace('_', ' ');
    if (descriptor.startsWith("/")) {
      descriptor = descriptor.substring(1);
    }
    if (descriptor.toLowerCase().endsWith(CSV_EXTENSION)) {
      descriptor = descriptor.substring(0, descriptor.lastIndexOf('.') - 1);
    }
    return descriptor;
  }


  /**
   * Helper method to extract the content from a file within a ZIP archive.
   *
   * @param inputStream
   * @return
   * @throws IOException
   */
  private static String extractFile(InputStream inputStream) throws IOException {
    // Extract file
    BufferedInputStream is = new BufferedInputStream(inputStream);
    int count;
    byte data[] = new byte[BUFFER];
    StringWriter dest = new StringWriter();
    while ((count = is.read(data, 0, BUFFER)) != -1) {
      dest.write(new String(data, StandardCharsets.UTF_8), 0, count);
    }
    dest.flush();
    dest.close();
    is.close();
    return dest.toString();
  }


  /**
   * Reads a reaction list from an input stream and creates a new hierarchical
   * model from the base model containing only the reactions given in that file.
   *
   * @param rIdx An array with indices of reactions to keep in the model. The
   * entries are assumed to be sorted.
   */
  public SBMLDocument createTissueModel(int[] rIdx) {
    String rIds[] = new String[rIdx.length];
    Model m = baseDoc.getModel();
    for (int i = 0; i < rIdx.length; i++) {
      rIds[i] = m.getReaction(rIdx[i]).getId();
    }
    SBMLDocument subDoc = SubModel.generateSubModel(m, null, null, rIds);
    // TODO: Recursively copy in all packages, for now only fbc on model
    if (m.getExtension(FBCConstants.shortLabel) != null) {
      FBCModelPlugin fbc = (FBCModelPlugin) m.getExtension(FBCConstants.shortLabel).clone();
      subDoc.getModel().addPlugin(FBCConstants.shortLabel, fbc);
      // Fix for some bug in JSBML:
      FBCModelPlugin fbcOrig = (FBCModelPlugin) m.getExtension(FBCConstants.shortLabel);
      for (GeneProduct gene : fbc.getListOfGeneProducts()) {
        if (!gene.isSetLabel()) {
          gene.setLabel(fbcOrig.getGeneProduct(gene.getId()).getLabel());
        }
      }
    }
    ModelCorrector.correct(subDoc);

    logger.info(format("\nIntial reaction count\t= {0,number,integer}\nReactions to keep\t= {1,number,integer}\nSubmodel reaction count\t= {2,number,integer}", baseDoc.getModel().getReactionCount(), rIdx.length, subDoc.getModel().getReactionCount()));
    return subDoc;
  }

  /**
   * Note: This only works as such if the base model is in the same directory as the submodel. Otherwise, the reference will be broken.
   * @param rIdx
   * @return
   */
  public SBMLDocument createTissueModelComp(int[] rIdx) {
    Model baseModel = baseDoc.getModel();
    SBMLDocument subDoc = new SBMLDocument(baseDoc.getLevel(), baseDoc.getVersion());
    CompSBMLDocumentPlugin comp = (CompSBMLDocumentPlugin) subDoc.createPlugin(COMP);
    ExternalModelDefinition emd = comp.createExternalModelDefinition(baseModel.getId());
    emd.setSource(baseDocFile.getName());
    emd.setModelRef(baseModel.getId());
    emd.setMd5(md5);
    Submodel submodel = ((CompModelPlugin) subDoc.createModel().createPlugin(COMP)).createSubmodel("tmp_id");
    submodel.setId(SBMLtools.nameToSId(baseModel.getName(), subDoc));
    submodel.setModelRef(baseModel.getId());
    // Now, we only want the IDs of those reactions that are to be deleted!!!
    for (int i = 0, j = 0; i < baseModel.getReactionCount(); i++) {
      if (rIdx[j] == i) {
        j++;
      } else {
        Deletion deletion = submodel.createDeletion();
        deletion.setIdRef(baseModel.getReaction(i).getId());
      }
    }
    logger.fine(format("\nModel reaction count = {0,number,integer}\nReaction index count = {1,number,integer}\nReactions to retain  = {2,number,integer}", baseModel.getReactionCount(), rIdx.length, submodel.getDeletionCount()));
    return subDoc;
  }

  /**
   * This method returns the complete  hash of the file passed
   * Modified from https://www.geeksforgeeks.org/how-to-generate-md5-checksum-for-files-in-java/.
   * @param file
   * @return
   * @throws IOException
   * @throws NoSuchAlgorithmException
   */
  private static String checksum(File file)
      throws IOException, NoSuchAlgorithmException
  {
    MessageDigest digest = MessageDigest.getInstance("MD5");
    // Get file input stream for reading the file
    // content
    FileInputStream fis = new FileInputStream(file);

    // Create byte array to read data in chunks
    byte[] byteArray = new byte[1024];
    int bytesCount = 0;

    // read the data from file and update that data in
    // the message digest
    while ((bytesCount = fis.read(byteArray)) != -1)
    {
      digest.update(byteArray, 0, bytesCount);
    };

    // close the input stream
    fis.close();

    // store the bytes returned by the digest() method
    byte[] bytes = digest.digest();

    // this array of bytes has bytes in decimal format
    // so we need to convert it into hexadecimal format

    // for this we create an object of StringBuilder
    // since it allows us to update the string i.e. its
    // mutable
    StringBuilder sb = new StringBuilder();

    // loop through the bytes array
    for (int i = 0; i < bytes.length; i++) {

      // the following line converts the decimal into
      // hexadecimal format and appends that to the
      // StringBuilder object
      sb.append(Integer
        .toString((bytes[i] & 0xff) + 0x100, 16)
        .substring(1));
    }

    // finally we return the complete hash
    return sb.toString();
  }

}
