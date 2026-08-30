package com.bytezone.dm3270.utilities;

import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// -----------------------------------------------------------------------------------//
public class SiteForm implements Site
// -----------------------------------------------------------------------------------//
{
  // O nome do logger e saida observavel - o logback imprime %logger{36} - e esta classe
  // se chamava Site quando os avisos de porta e modelo foram escritos. Site agora e a
  // interface, no mesmo pacote, entao o nome resolvido continua identico (§5.13).
  private static final Logger logger = LoggerFactory.getLogger (Site.class);

  // make these StringProperty and use a table
  public final TextField name = new TextField ();
  public final TextField url = new TextField ();
  public final TextField port = new TextField ();
  public final CheckBox extended = new CheckBox ();
  public final TextField model = new TextField ();
  public final CheckBox plugins = new CheckBox ();
  public final CheckBox ssl = new CheckBox ();
  public final CheckBox trustAll = new CheckBox ();
  public final TextField folder = new TextField ();

  private final TextField[] textFieldList =
      { name, url, port, null, model, null, null, null, folder };
  private final CheckBox[] checkBoxFieldList =
      { null, null, null, extended, null, plugins, ssl, trustAll, null };

  // ---------------------------------------------------------------------------------//
  public SiteForm (String name, String url, int port, boolean extended, int model,
      boolean plugins, boolean ssl, boolean trustAll, String folder)
  // ---------------------------------------------------------------------------------//
  {
    this.name.setText (name);
    this.url.setText (url);
    this.port.setText (port == 23 && name.isEmpty () ? "" : port + "");
    this.extended.setSelected (extended);
    this.model.setText (model == 2 && name.isEmpty () ? "" : model + "");
    this.plugins.setSelected (plugins);
    this.ssl.setSelected (ssl);
    this.trustAll.setSelected (trustAll);
    this.folder.setText (folder);
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public String getName ()
  // ---------------------------------------------------------------------------------//
  {
    return name.getText ();
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public String getURL ()
  // ---------------------------------------------------------------------------------//
  {
    return url.getText ();
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public int getPort ()
  // ---------------------------------------------------------------------------------//
  {
    try
    {
      int portValue = Integer.parseInt (port.getText ());
      if (portValue <= 0)
      {
        logger.warn ("Invalid port value: {}", port.getText ());
        port.setText ("23");
        portValue = 23;
      }
      return portValue;
    }
    catch (NumberFormatException e)
    {
      logger.warn ("Invalid port value: {}", port.getText (), e);
      port.setText ("23");
      return 23;
    }
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public boolean getExtended ()
  // ---------------------------------------------------------------------------------//
  {
    return extended.isSelected ();
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public int getModel ()
  // ---------------------------------------------------------------------------------//
  {
    try
    {
      int modelValue = Integer.parseInt (model.getText ());
      if (modelValue < 2 || modelValue > 5)
      {
        logger.warn ("Invalid model value: {}", model.getText ());
        model.setText ("2");
        modelValue = 2;
      }
      return modelValue;
    }
    catch (NumberFormatException e)
    {
      logger.warn ("Invalid model value: {}", model.getText (), e);
      model.setText ("2");
      return 2;
    }
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public boolean getPlugins ()
  // ---------------------------------------------------------------------------------//
  {
    return plugins.isSelected ();
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public String getFolder ()
  // ---------------------------------------------------------------------------------//
  {
    return folder.getText ();
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public boolean getSsl ()
  // ---------------------------------------------------------------------------------//
  {
    return ssl.isSelected ();
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public boolean getTrustAll ()
  // ---------------------------------------------------------------------------------//
  {
    return trustAll.isSelected ();
  }

  // ---------------------------------------------------------------------------------//
  public TextField getTextField (int index)
  // ---------------------------------------------------------------------------------//
  {
    return textFieldList[index];
  }

  // ---------------------------------------------------------------------------------//
  public CheckBox getCheckBoxField (int index)
  // ---------------------------------------------------------------------------------//
  {
    return checkBoxFieldList[index];
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public String toString ()
  // ---------------------------------------------------------------------------------//
  {
    return String.format ("Site [name=%s, url=%s, port=%d, folder=%s]", getName (),
        getURL (), getPort (), getFolder ());
  }
}