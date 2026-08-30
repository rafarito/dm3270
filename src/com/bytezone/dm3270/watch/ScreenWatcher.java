package com.bytezone.dm3270.watch;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.bytezone.dm3270.datasets.DatasetSummary;
import com.bytezone.dm3270.datasets.Dataset;
import com.bytezone.dm3270.datasets.DatasetStore;
import com.bytezone.dm3270.datasets.Member;
import com.bytezone.dm3270.screen.Field;
import com.bytezone.dm3270.screen.ScreenDimensions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// created by FieldManager
// used by ScreenChangeListener (ScreenPacker, TransfersStage, TransferMenu)
// used by DownloadDialog (via TransferMenu)
// used by UploadDialog (via TransferMenu)
// -----------------------------------------------------------------------------------//
public class ScreenWatcher
// -----------------------------------------------------------------------------------//
{
  private static final Logger logger = LoggerFactory.getLogger (ScreenWatcher.class);

  private static final String[] tsoMenus =
      { "Menu", "List", "Mode", "Functions", "Utilities", "Help" };
  private static final String[] pdsMenus =
      { "Menu", "Functions", "Confirm", "Utilities", "Help" };
  private static final String[] memberMenus =
      { "Menu", "Functions", "Utilities", "Help" };
  private static final String SPLIT_LINE = ".  .  .  .  .  .  .  .  .  .  .  .  .  "
      + ".  .  .  .  .  .  .  .  .  .  .  .  .  .";
  private static final String EXCLUDE_LINE = "-  -  -  -  -  -  -  -  -  -  -  -";
  private static final String segment = "[A-Z@#$][-A-Z0-9@#$]{0,7}";
  private static final Pattern datasetNamePattern =
      Pattern.compile (segment + "(\\." + segment + "){0,21}");
  private static final Pattern memberNamePattern = Pattern.compile (segment);

  /*
   * Os cinco formatos de lista de dataset do DSLIST. Cada um carrega os proprios
   * deslocamentos de coluna e nao guarda estado nenhum, entao uma instancia de cada basta.
   */
  private static final DatasetListLayout tracksLayout = new TracksLayout ();
  private static final DatasetListLayout dsorgLayout = new DsorgLayout ();
  private static final DatasetListLayout volumeLayout = new VolumeLayout ();
  private static final DatasetListLayout catalogLayout = new CatalogLayout ();
  private static final DatasetListLayout underscoreLayout = new UnderscoreLayout ();

  private static final String ispfScreen = "ISPF Primary Option Menu";
  private static final String zosScreen = "z/OS Primary Option Menu";
  private static final String ispfShell = "ISPF Command Shell";

  private final ScreenFields fieldManager;
  private final ScreenDimensions screenDimensions;
  private final DatasetStore datasetStore;

  private final Map<String, DatasetSummary> siteDatasets = new TreeMap<> ();
  private final List<DatasetSummary> screenDatasets = new ArrayList<> ();
  private final List<DatasetSummary> screenMembers = new ArrayList<> ();
  private final List<String> recentDatasetNames = new ArrayList<> ();

  private String datasetsMatching;
  private String datasetsOnVolume;

  private Field tsoCommandField;
  private boolean isTSOCommandScreen;
  private boolean isDatasetList;
  private boolean isMemberList;
  private boolean isSplitScreen;
  private int promptFieldLine;

  private String currentPDS = "";
  private String singleDataset = "";
  private String userid = "";
  private String prefix = "";

  // ---------------------------------------------------------------------------------//
  public ScreenWatcher (ScreenFields fieldManager, ScreenDimensions screenDimensions,
      DatasetStore datasetStore)
  // ---------------------------------------------------------------------------------//
  {
    this.fieldManager = fieldManager;
    this.screenDimensions = screenDimensions;
    this.datasetStore = datasetStore;
  }

  // ---------------------------------------------------------------------------------//
  public Field getTSOCommandField ()
  // ---------------------------------------------------------------------------------//
  {
    return tsoCommandField;
  }

  // ---------------------------------------------------------------------------------//
  public boolean isTSOCommandScreen ()
  // ---------------------------------------------------------------------------------//
  {
    return isTSOCommandScreen;
  }

  // ---------------------------------------------------------------------------------//
  public String getUserid ()
  // ---------------------------------------------------------------------------------//
  {
    return userid;
  }

  // ---------------------------------------------------------------------------------//
  public String getPrefix ()
  // ---------------------------------------------------------------------------------//
  {
    return prefix;
  }

  // called by UploadDialog
  // called by DownloadDialog
  // ---------------------------------------------------------------------------------//
  public Optional<DatasetSummary> getDataset (String datasetName)
  // ---------------------------------------------------------------------------------//
  {
    if (siteDatasets.containsKey (datasetName))
      return Optional.of (siteDatasets.get (datasetName));
    return Optional.empty ();
  }

  // ---------------------------------------------------------------------------------//
  public List<DatasetSummary> getDatasets ()
  // ---------------------------------------------------------------------------------//
  {
    return screenDatasets;
  }

  // ---------------------------------------------------------------------------------//
  public List<DatasetSummary> getMembers ()
  // ---------------------------------------------------------------------------------//
  {
    return screenMembers;
  }

  // ---------------------------------------------------------------------------------//
  public String getCurrentPDS ()
  // ---------------------------------------------------------------------------------//
  {
    return currentPDS;
  }

  // ---------------------------------------------------------------------------------//
  public String getSingleDataset ()
  // ---------------------------------------------------------------------------------//
  {
    return singleDataset;
  }

  // ---------------------------------------------------------------------------------//
  public List<String> getRecentDatasets ()
  // ---------------------------------------------------------------------------------//
  {
    return recentDatasetNames;
  }

  // called by FieldManager after building a new screen
  // ---------------------------------------------------------------------------------//
  public void check ()
  // ---------------------------------------------------------------------------------//
  {
    tsoCommandField = null;
    isTSOCommandScreen = false;
    isDatasetList = false;
    isMemberList = false;
    isSplitScreen = false;
    screenDatasets.clear ();
    screenMembers.clear ();
    //    currentDataset = "";
    //    singleDataset = "";
    promptFieldLine = -1;

    List<Field> screenFields = fieldManager.getFields ();
    if (screenFields.size () <= 2)
      return;

    isSplitScreen = checkSplitScreen ();
    if (isSplitScreen)
      return;

    checkMenu ();

    isTSOCommandScreen = checkTSOCommandScreen (screenFields);
    if (isTSOCommandScreen)
    {

    }
    else if (hasPromptField ())
    {
      if (prefix.isEmpty ())
        checkPrefixScreen (screenFields);       // initial ISPF screen

      isDatasetList = checkDatasetList (screenFields);
      if (isDatasetList)
      {
        //        System.out.println ("Dataset list");
      }
      else
      {
        isMemberList = checkMemberList (screenFields);
        if (isMemberList)
        {
          //          System.out.println ("Member list of " + currentDataset);
        }
        else
          checkSingleDataset (screenFields);
      }
    }
  }

  // ---------------------------------------------------------------------------------//
  private void checkMenu ()
  // ---------------------------------------------------------------------------------//
  {
    if (true)
      return;

    List<Field> rowFields = fieldManager.getRowFields (0, 1);
    dumpFields (rowFields);

    if (rowFields.size () > 1 && rowFields.size () < 10)
    {
      Field menuField = rowFields.get (0);
      String text = menuField.getText ();
      if (" Menu".equals (text) && menuField.isAlphanumeric () && menuField.isProtected ()
          && menuField.isVisible () && menuField.isIntensified ())
      {
        logger.debug ("Possible menu");
      }
    }
  }

  // ---------------------------------------------------------------------------------//
  private boolean checkSplitScreen ()
  // ---------------------------------------------------------------------------------//
  {
    return fieldManager.getFields ().parallelStream ()
        .filter (f -> f.isProtected () && f.getDisplayLength () == 79
            && f.getFirstLocation () % screenDimensions.columns == 1
            && SPLIT_LINE.equals (f.getText ()))
        .findAny ().isPresent ();
  }

  // ---------------------------------------------------------------------------------//
  private boolean hasPromptField ()
  // ---------------------------------------------------------------------------------//
  {
    List<Field> rowFields = fieldManager.getRowFields (1, 3);
    for (int i = 0; i < rowFields.size (); i++)
    {
      Field field = rowFields.get (i);
      String text = field.getText ();

      int column = field.getFirstLocation () % screenDimensions.columns;
      int nextFieldNo = i + 1;

      if (nextFieldNo < rowFields.size () && column == 1
          && ("Command ===>".equals (text) || "Option ===>".equals (text)))
      {
        Field nextField = rowFields.get (nextFieldNo);
        int length = nextField.getDisplayLength ();
        boolean modifiable = nextField.isUnprotected ();
        boolean visible = !nextField.isHidden ();

        if ((length == 66 || length == 48) && visible && modifiable)
        {
          tsoCommandField = nextField;
          promptFieldLine = field.getFirstLocation () / screenDimensions.columns;
          return true;
        }
      }
    }

    tsoCommandField = null;
    return false;
  }

  // ---------------------------------------------------------------------------------//
  private void checkPrefixScreen (List<Field> screenFields)
  // ---------------------------------------------------------------------------------//
  {
    if (screenFields.size () < 74)
      return;

    Field field = screenFields.get (10);
    String heading = field.getText ();
    if (!ispfScreen.equals (heading) && !zosScreen.equals (heading))
      return;

    if (!fieldManager.textMatches (23, " User ID . :", 457))
      return;

    field = screenFields.get (24);
    if (field.getFirstLocation () != 470)
      return;

    userid = field.getText ().trim ();

    if (!fieldManager.textMatches (72, " TSO prefix:", 1017))
      return;

    field = screenFields.get (73);
    if (field.getFirstLocation () != 1030)
      return;

    prefix = field.getText ().trim ();
  }

  // ---------------------------------------------------------------------------------//
  private boolean checkTSOCommandScreen (List<Field> screenFields)
  // ---------------------------------------------------------------------------------//
  {
    if (screenFields.size () < 19)
      return false;

    if (!fieldManager.textMatches (10, ispfShell))
      return false;

    int workstationFieldNo = 13;
    String workstationText = "Enter TSO or Workstation commands below:";
    if (!fieldManager.textMatches (workstationFieldNo, workstationText))
      if (!fieldManager.textMatches (++workstationFieldNo, workstationText))
        return false;

    if (!listMatchesArray (fieldManager.getMenus (), tsoMenus))
      return false;

    Field field = screenFields.get (workstationFieldNo + 5);
    if (field.getDisplayLength () != 234)
      return false;

    tsoCommandField = field;
    return true;
  }

  // ---------------------------------------------------------------------------------//
  private boolean checkDatasetList (List<Field> screenFields)
  // ---------------------------------------------------------------------------------//
  {
    if (screenFields.size () < 21)
      return false;

    List<Field> rowFields = fieldManager.getRowFields (2, 2);
    if (rowFields.size () == 0)
      return false;

    String text = rowFields.get (0).getText ();
    if (!text.startsWith ("DSLIST - Data Sets "))
      return false;

    String locationText = "";
    int pos = text.indexOf ("Row ");
    if (pos > 0)
      locationText = text.substring (19, pos).trim ();
    else
      locationText = text.substring (19).trim ();

    datasetsOnVolume = "";
    datasetsMatching = "";

    if (locationText.startsWith ("on volume "))
      datasetsOnVolume = locationText.substring (10);
    else if (locationText.startsWith ("Matching "))
      datasetsMatching = locationText.substring (9);
    else
    {
      // Could be: Matched in list REFLIST
      logger.warn ("Unexpected text: {}", locationText);
      return false;
    }

    rowFields = fieldManager.getRowFields (5, 2);
    if (rowFields.size () < 3)
      return false;

    if (!rowFields.get (0).getText ().startsWith ("Command - Enter"))
      return false;

    DatasetListLayout layout = selectLayout (rowFields);
    if (layout == null)
      return false;

    // A geometria da lista vem do layout: quantas linhas cada dataset ocupa, e onde ela
    // comeca.
    int linesPerDataset = layout.linesPerDataset ();
    int nextLine = layout.firstDataRow ();

    while (nextLine < screenDimensions.rows)
    {
      rowFields = fieldManager.getRowFields (nextLine, linesPerDataset);
      if (rowFields.size () <= 1)
        break;

      String lineText = rowFields.get (0).getText ();
      if (lineText.length () < 10)
        break;

      String datasetName = lineText.substring (9).trim ();
      if (datasetName.length () > 44)
      {
        logger.warn ("Dataset name too long: {}", datasetName);
        break;
      }

      if (datasetNamePattern.matcher (datasetName).matches ())
        addDataset (datasetName, layout, rowFields);
      else
      {
        // check for excluded datasets
        if (!EXCLUDE_LINE.equals (datasetName))
          logger.warn ("Invalid dataset name: {}", datasetName);

        // what about GDGs?
      }

      nextLine += linesPerDataset;
      if (linesPerDataset > 1)
        nextLine++;                           // skip the row of hyphens
    }

    return true;
  }

  /*
   * Qual dos cinco formatos esta na tela, ou null se nenhum deles.
   *
   * A decisao e uma arvore, e nao uma lista: a quantidade de campos da linha de titulos
   * escolhe o ramo, os titulos confirmam, e os dois formatos de varias linhas por dataset
   * ainda se separam pelo marcador da linha 7 - a palavra "Catalog" ou uma linha de tracos.
   *
   * Os tres avisos dizem ONDE a arvore parou, e sao a razao de ela continuar sendo um switch
   * aqui em vez de um matches () declarado por layout: uma lista plana de predicados perde a
   * informacao de quao longe o reconhecimento chegou, e reconstrui-la para os avisos exigiria
   * repetir a arvore ao lado dela - mais duplicacao do que a que este trabalho desfez, e nao
   * menos. O que era duplicado de verdade eram os deslocamentos de coluna, e esses ja sairam.
   */
  // ---------------------------------------------------------------------------------//
  private DatasetListLayout selectLayout (List<Field> headingRow)
  // ---------------------------------------------------------------------------------//
  {
    DatasetListLayout layout = null;

    switch (headingRow.size ())
    {
      case 3:
        String heading = headingRow.get (1).getText ().trim ();
        if (heading.startsWith ("Tracks"))
          layout = tracksLayout;
        else if (heading.startsWith ("Dsorg"))
          layout = dsorgLayout;
        break;

      case 4:
        String message = headingRow.get (1).getText ().trim ();
        heading = headingRow.get (2).getText ().trim ();
        if ("Volume".equals (heading) && "Message".equals (message))
          layout = volumeLayout;
        break;

      case 6:
        message = headingRow.get (1).getText ().trim ();
        heading = headingRow.get (2).getText ().trim ();
        if ("Volume".equals (heading) && "Message".equals (message))
        {
          List<Field> markerRow = fieldManager.getRowFields (7);
          if (markerRow.size () == 1)
          {
            String line = markerRow.get (0).getText ().trim ();
            if (line.equals ("Catalog"))
              layout = catalogLayout;
            else if (line.startsWith ("--"))
              layout = underscoreLayout;
            else
              logger.warn ("Expected 'Catalog' or underscores: {}", line);
          }
        }
        break;

      default:
        logger.warn ("Unexpected number of fields: {}", headingRow.size ());
    }

    if (layout == null)
    {
      logger.warn ("Screen not recognised");
      dumpFields (headingRow);
    }

    return layout;
  }

  /*
   * Acumula um dataset visto na tela e entrega a leitura ao layout.
   *
   * O summary e o mesmo objeto entre telas quando o nome se repete - a tabela do assistant
   * depende disso, porque e a mutacao dele que faz a linha mostrar dados que so aparecem numa
   * tela posterior. O delta e novo a cada leitura, e vai para a persistencia SEMPRE, mesmo
   * quando o layout nao le nada: nesse caso grava so o nome.
   */
  // ---------------------------------------------------------------------------------//
  private void addDataset (String datasetName, DatasetListLayout layout,
      List<Field> rowFields)
  // ---------------------------------------------------------------------------------//
  {
    DatasetSummary dataset;
    if (siteDatasets.containsKey (datasetName))
      dataset = siteDatasets.get (datasetName);
    else
    {
      dataset = new DatasetSummary (datasetName);
      siteDatasets.put (datasetName, dataset);
    }

    screenDatasets.add (dataset);
    Dataset ds = new Dataset (dataset.getDatasetName ());

    layout.read (dataset, rowFields, ds);

    datasetStore.update (ds);
  }

  // ---------------------------------------------------------------------------------//
  private boolean checkMemberList (List<Field> screenFields)
  // ---------------------------------------------------------------------------------//
  {
    if (screenFields.size () < 14)
      return false;

    if (listMatchesArray (fieldManager.getMenus (), pdsMenus))
      return checkMemberList1 (screenFields);

    if (listMatchesArray (fieldManager.getMenus (), memberMenus))
      return checkMemberList2 (screenFields);

    return false;
  }

  // ---------------------------------------------------------------------------------//
  private boolean checkMemberList1 (List<Field> screenFields)
  // ---------------------------------------------------------------------------------//
  {
    Field field = screenFields.get (8);
    int location = field.getFirstLocation ();
    if (location != 161)
      return false;

    String mode = field.getText ().trim ();
    int[] tabs1 = null;
    int[] tabs2 = null;

    switch (mode)
    {
      case "LIBRARY":                             // 3.1
        tabs1 = new int[] { 12, 25, 38, 47 };
        tabs2 = new int[] { 12, 21, 31, 43 };
        break;

      case "EDIT":                                // 3.4:e
      case "BROWSE":                              // 3.4:b
      case "VIEW":                                // 3.4:v
      case "DSLIST":                              // 3.4:m
        tabs1 = new int[] { 9, 21, 33, 42 };
        tabs2 = new int[] { 9, 17, 25, 36 };
        break;
      default:
        logger.warn ("Unexpected mode1: [{}]", mode);
        return false;
    }

    field = screenFields.get (9);
    if (field.getFirstLocation () != 179)
      return false;

    String datasetName = field.getText ().trim ();
    currentPDS = datasetName;
    Dataset ds = new Dataset (datasetName);

    List<Field> headings = fieldManager.getRowFields (4);

    for (int row = 5; row < screenDimensions.rows; row++)
    {
      List<Field> rowFields = fieldManager.getRowFields (row);
      if (rowFields.size () != 4 || rowFields.get (1).getText ().equals ("**End** "))
        break;

      String memberName = rowFields.get (1).getText ().trim ();
      Matcher matcher = memberNamePattern.matcher (memberName);
      if (!matcher.matches ())
      {
        logger.warn ("Invalid member name: {}", memberName);
        break;
      }
      String details = rowFields.get (3).getText ();

      //      Dataset member = new Dataset (datasetName + "(" + memberName + ")");
      //      screenMembers.add (member);
      DatasetSummary member = addMember (datasetName, memberName);
      Member m = new Member (ds, memberName);

      if (headings.size () == 7 || headings.size () == 10)
        screenType1 (member, details, tabs1, m);
      else if (headings.size () == 13)
        screenType2 (member, details, tabs2, m);
      else
        logger.warn ("Headings size: {}", headings.size ());
    }

    return true;
  }

  // ---------------------------------------------------------------------------------//
  private boolean checkMemberList2 (List<Field> screenFields)
  // ---------------------------------------------------------------------------------//
  {
    Field field = screenFields.get (7);
    int location = field.getFirstLocation ();
    if (location != 161)
      return false;

    String mode = field.getText ().trim ();
    if (!(mode.equals ("EDIT")      // Menu option 1 (browse mode not selected)
        || mode.equals ("BROWSE")   // Menu option 1 (browse mode selected)
        || mode.equals ("VIEW")))   // Menu option 2
    {
      logger.warn ("Unexpected mode2: [{}]", mode);
      return false;
    }

    int[] tabs1 = { 12, 25, 38, 47 };
    int[] tabs2 = { 12, 21, 31, 43 };

    field = screenFields.get (8);
    if (field.getFirstLocation () != 170)
      return false;
    String datasetName = field.getText ().trim ();
    currentPDS = datasetName;
    Dataset ds = new Dataset (datasetName);

    List<Field> headings = fieldManager.getRowFields (4);

    int screenType = 0;
    if (headings.size () == 10
        && fieldManager.textMatchesTrim (headings.get (5), "Created"))
      screenType = 1;
    else if (headings.size () == 13
        && fieldManager.textMatchesTrim (headings.get (5), "Init"))
      screenType = 2;
    else
      dumpFields (headings);

    if (screenType == 0)
      return false;

    for (int row = 5; row < screenDimensions.rows; row++)
    {
      List<Field> rowFields = fieldManager.getRowFields (row);
      if (rowFields.size () != 4 || rowFields.get (1).getText ().equals ("**End** "))
        break;

      String memberName = rowFields.get (1).getText ().trim ();
      Matcher matcher = memberNamePattern.matcher (memberName);
      if (!matcher.matches ())
      {
        logger.warn ("Invalid member name: {}", memberName);
        break;
      }
      String details = rowFields.get (3).getText ();

      DatasetSummary member = addMember (datasetName, memberName);
      Member m = new Member (ds, memberName);

      if (screenType == 1)
        screenType1 (member, details, tabs1, m);
      else if (screenType == 2)
        screenType2 (member, details, tabs2, m);
      else
        dumpFields (rowFields);
    }

    return true;
  }

  // ---------------------------------------------------------------------------------//
  private DatasetSummary addMember (String pdsName, String memberName)
  // ---------------------------------------------------------------------------------//
  {
    String datasetName = pdsName + "(" + memberName.trim () + ")";
    DatasetSummary member;

    if (siteDatasets.containsKey (datasetName))
      member = siteDatasets.get (datasetName);
    else
    {
      member = new DatasetSummary (datasetName);
      siteDatasets.put (datasetName, member);
    }

    screenMembers.add (member);

    return member;
  }

  // ---------------------------------------------------------------------------------//
  private void screenType1 (DatasetSummary member, String details, int[] tabs, Member m)
  // ---------------------------------------------------------------------------------//
  {
    member.setCreated (details.substring (tabs[0], tabs[1]).trim ());
    member.setReferredDate (details.substring (tabs[1], tabs[2]).trim ());
    member.setReferredTime (details.substring (tabs[2], tabs[3]).trim ());
    member.setCatalog (details.substring (tabs[3]).trim ());
    member.setExtents (
        DatasetDetails.getInteger ("Ext:", details.substring (0, tabs[0]).trim ()));

    int size = DatasetDetails.getInteger ("Size", details.substring (0, tabs[0]).trim ());
    String created = details.substring (tabs[0], tabs[1]);
    String changed = details.substring (tabs[1], tabs[3]);
    String id = details.substring (tabs[3]).trim ();

    m.setDates (created, changed);
    m.setID (id);
    m.setSize (size);

    datasetStore.update (m);
  }

  // ---------------------------------------------------------------------------------//
  private void screenType2 (DatasetSummary member, String details, int[] tabs, Member m)
  // ---------------------------------------------------------------------------------//
  {
    //    String size = details.substring (0, tabs[0]);
    //    String init = details.substring (tabs[0], tabs[1]);
    //    String mod = details.substring (tabs[1], tabs[2]);
    String vvmm = details.substring (tabs[2], tabs[3]).trim ();
    String id = details.substring (tabs[3]).trim ();
    //    System.out.printf ("[%s]%n", vvmm);

    int size = DatasetDetails.getInteger ("Size", details.substring (0, tabs[0]).trim ());
    int init =
        DatasetDetails.getInteger ("Init", details.substring (tabs[0], tabs[1]).trim ());
    int mod = DatasetDetails.getInteger ("Mod", details.substring (tabs[1], tabs[2]).trim ());

    if (!vvmm.isEmpty ())
    {
      int vv = DatasetDetails.getInteger ("VV", vvmm.substring (0, 2));
      int mm = DatasetDetails.getInteger ("MM", vvmm.substring (3));
      m.setSize (size, init, mod, vv, mm);
    }

    member.setCatalog (id);       // (mis)use the catalog column
    member.setExtents (size);             // (mis)use the extents column

    m.setID (id);

    datasetStore.update (m);
  }

  // ---------------------------------------------------------------------------------//
  private void checkSingleDataset (List<Field> fields)
  // ---------------------------------------------------------------------------------//
  {
    if (fields.size () < 13)
      return;

    List<Field> rowFields = fieldManager.getRowFields (0, 3);
    if (rowFields.size () == 0)
      return;

    int fldNo = 0;
    for (Field field : rowFields)
    {
      if (field.getFirstLocation () % screenDimensions.columns == 1
          && (field.getDisplayLength () == 10 || field.getDisplayLength () == 9)
          && fldNo + 2 < rowFields.size ())
      {
        String text1 = field.getText ().trim ();
        String text2 = rowFields.get (fldNo + 1).getText ().trim ();
        String text3 = rowFields.get (fldNo + 2).getText ();

        if ((text1.equals ("EDIT") || text1.equals ("VIEW") || text1.equals ("BROWSE"))
            && (text3.equals ("Columns") || text3.equals ("Line")))
        {
          int pos = text2.indexOf (' ');
          String datasetName = pos < 0 ? text2 : text2.substring (0, pos);
          String memberName = "";
          int pos1 = datasetName.indexOf ('(');
          if (pos1 > 0 && datasetName.endsWith (")"))
          {
            memberName = datasetName.substring (pos1 + 1, datasetName.length () - 1);
            datasetName = datasetName.substring (0, pos1);
          }
          Matcher matcher = datasetNamePattern.matcher (datasetName);
          if (matcher.matches ())
          {
            //  System.out.printf ("%-11s %-20s %s%n", text1, datasetName, memberName);
            singleDataset = datasetName;
            if (!memberName.isEmpty ())
              singleDataset += "(" + memberName + ")";
            if (!recentDatasetNames.contains (singleDataset))
              recentDatasetNames.add (singleDataset);
          }
        }
      }
      fldNo++;
    }
  }

  /*
   * Um nome de dataset valido: segmentos de ate oito caracteres separados por ponto. Usado
   * aqui e pelo CatalogLayout, que confere a coluna de catalogo antes de aceita-la - a mesma
   * coluna tambem carrega mensagens.
   */
  // ---------------------------------------------------------------------------------//
  static boolean isDatasetName (String text)
  // ---------------------------------------------------------------------------------//
  {
    return datasetNamePattern.matcher (text).matches ();
  }

  // ---------------------------------------------------------------------------------//
  private boolean listMatchesArray (List<String> list, String[] array)
  // ---------------------------------------------------------------------------------//
  {
    if (list.size () != array.length)
      return false;
    int i = 0;
    for (String text : list)
      if (!array[i++].equals (text))
        return false;
    return true;
  }

  // ---------------------------------------------------------------------------------//
  private void dumpFields (List<Field> fields)
  // ---------------------------------------------------------------------------------//
  {
    for (Field field : fields)
      logger.debug ("{}", field);
    logger.debug ("-------------------------");
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public String toString ()
  // ---------------------------------------------------------------------------------//
  {
    StringBuilder text = new StringBuilder ();

    text.append ("Screen details:\n");
    text.append (String.format ("TSO screen ........ %s%n", isTSOCommandScreen));
    text.append (String.format ("Prompt field ...... %s%n", tsoCommandField));
    text.append (String.format ("Prompt line ....... %d%n", promptFieldLine));
    text.append (String.format ("Dataset list ...... %s%n", isDatasetList));
    text.append (String.format ("Members list ...... %s%n", isMemberList));
    text.append (String.format ("Current dataset ... %s%n", currentPDS));
    text.append (String.format ("Single dataset .... %s%n", singleDataset));
    text.append (String.format ("Userid/prefix ..... %s / %s%n", userid, prefix));
    text.append (String.format ("Datasets for ...... %s%n", datasetsMatching));
    text.append (String.format ("Volume ............ %s%n", datasetsOnVolume));
    text.append (String.format ("Datasets .......... %s%n",
        screenDatasets == null ? "" : screenDatasets.size ()));
    text.append (String.format ("Recent datasets ... %s%n",
        screenDatasets == null ? "" : recentDatasetNames.size ()));
    int i = 0;
    for (String datasetName : recentDatasetNames)
      text.append (String.format ("            %3d ... %s%n", ++i, datasetName));

    return text.toString ();
  }


}