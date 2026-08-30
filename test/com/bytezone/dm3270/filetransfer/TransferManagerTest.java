package com.bytezone.dm3270.filetransfer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.bytezone.dm3270.filetransfer.TransferManager.TransferStatus;

// -----------------------------------------------------------------------------------//
@DisplayName ("TransferManager - ciclo de vida de uma transferencia")
class TransferManagerTest
// -----------------------------------------------------------------------------------//
{
  // O gerenciador recebe a fonte do prefixo do usuario, e nao a tela inteira.
  private static final java.util.function.Supplier<String> NO_PREFIX = () -> "";
  private static final com.bytezone.dm3270.utilities.SiteForm NO_SITE = null;

  private String originalHome;
  private TransferManager manager;
  private RecordingListener listener;

  // closeTransfer () grava o arquivo baixado sob ~/dm3270/files: o teste redireciona
  // o HOME para nao sujar o diretorio do usuario, e cria a pasta que FileSaver espera.
  @BeforeEach
  void setUp (@TempDir File directory) throws IOException
  {
    originalHome = System.getProperty ("user.home");
    System.setProperty ("user.home", directory.getAbsolutePath ());
    Files.createDirectories (directory.toPath ().resolve ("dm3270").resolve ("files"));

    manager = new TransferManager (NO_PREFIX, NO_SITE);
    listener = new RecordingListener ();
    manager.addTransferListener (listener);
  }

  @AfterEach
  void tearDown ()
  {
    System.setProperty ("user.home", originalHome);
  }

  // ---------------------------------------------------------------------------------//
  //  Construcao dos campos estruturados
  // ---------------------------------------------------------------------------------//

  private static byte[] ascii (String text)
  {
    return text.getBytes (java.nio.charset.StandardCharsets.US_ASCII);
  }

  // Um campo D0 de abertura que declara MSG ou DATA.
  private static FileTransferOutboundSF openRecord (String contents)
  {
    ByteArrayOutputStream out = new ByteArrayOutputStream ();
    out.write (0xD0);
    out.write (0x00);
    out.write (0x12);
    out.write (0x03);
    out.write (0x09);
    out.write (ascii (contents), 0, 7);

    byte[] buffer = out.toByteArray ();

    return new FileTransferOutboundSF (buffer, 0, buffer.length);
  }

  // Um campo D0 de dados, com um DataRecord no fim.
  private static FileTransferOutboundSF dataRecord (String content)
  {
    ByteArrayOutputStream out = new ByteArrayOutputStream ();
    out.write (0xD0);
    out.write (0x47);
    out.write (0x04);
    out.write (0xC0);
    out.write (0x80);
    out.write (0x61);
    out.write (0x00);
    out.write (DataRecord.HEADER_LENGTH + content.length ());
    out.write (ascii (content), 0, content.length ());

    byte[] buffer = out.toByteArray ();

    return new FileTransferOutboundSF (buffer, 0, buffer.length);
  }

  // Abre a transferencia pelo caminho normal, agora que ele nao exige mais a tela.
  private Transfer downloadOf (String datasetName)
  {
    manager.prepareTransfer (new IndFileCommand ("IND$FILE GET " + datasetName));
    listener.events.clear ();

    return manager.getTransfer ().get ();
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("reconhecimento do comando IND$FILE")
  class CommandRecognition
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("um comando de download e reconhecido")
    void recognisesGet ()
    {
      assertTrue (TransferManager.isIndfileCommand ("IND$FILE GET MEU.DATASET"));
    }

    @Test
    @DisplayName ("um comando que nao e IND$FILE nao muda o estado")
    void ignoresOtherCommands ()
    {
      // tsoCommand () so toca a tela quando reconhece o comando
      manager.tsoCommand ("LISTCAT");

      assertFalse (manager.getTransfer ().isPresent ());
      assertTrue (listener.events.isEmpty ());
    }

    @Test
    @DisplayName ("um comando reconhecido abre a transferencia")
    void recognisedCommandOpensTransfer ()
    {
      manager.tsoCommand ("IND$FILE GET MEU.DATASET");

      assertTrue (manager.getTransfer ().isPresent ());
      assertEquals ("MEU.DATASET", manager.getTransfer ().get ().getDatasetName ());
    }

    @Test
    @DisplayName ("o prefixo do usuario e usado como TLQ quando o dataset nao tem HLQ")
    void prefixBecomesTlq ()
    {
      TransferManager withPrefix = new TransferManager ( () -> "USER01", NO_SITE);

      withPrefix.tsoCommand ("IND$FILE GET DADOS");

      assertEquals ("USER01.DADOS",
                    withPrefix.getTransfer ().get ().getDatasetName ());
    }

    @Test
    @DisplayName ("um segundo comando nao substitui a transferencia em andamento")
    void secondCommandIsIgnored ()
    {
      manager.tsoCommand ("IND$FILE GET PRIMEIRO.DATASET");
      manager.tsoCommand ("IND$FILE GET SEGUNDO.DATASET");

      assertEquals ("PRIMEIRO.DATASET",
                    manager.getTransfer ().get ().getDatasetName ());
    }

    @Test
    @DisplayName ("um IND$FILE malformado nao abre transferencia")
    void malformedCommandIsIgnored ()
    {
      // o comando casa com o padrao mas IndFileCommand recusa: sem nome de dataset
      manager.tsoCommand ("IND$FILE GET");

      assertFalse (manager.getTransfer ().isPresent ());
    }

    @Test
    @DisplayName ("um upload sem arquivo local desiste antes de tocar a tela")
    void uploadWithoutFileReturnsEarly ()
    {
      IndFileCommand command = new IndFileCommand ("IND$FILE PUT MEU.DATASET");

      manager.prepareTransfer (command);

      assertFalse (manager.getTransfer ().isPresent ());
      assertTrue (listener.events.isEmpty ());
    }

    @Test
    @DisplayName ("um upload de arquivo inexistente tambem desiste")
    void uploadOfMissingFileReturnsEarly (@TempDir File directory)
    {
      IndFileCommand command = new IndFileCommand ("IND$FILE PUT MEU.DATASET");
      command.setLocalFile (new File (directory, "nao-existe.txt"));

      manager.prepareTransfer (command);

      assertFalse (manager.getTransfer ().isPresent ());
    }

    @Test
    @DisplayName ("um upload de diretorio em vez de arquivo desiste")
    void uploadOfDirectoryReturnsEarly (@TempDir File directory)
    {
      IndFileCommand command = new IndFileCommand ("IND$FILE PUT MEU.DATASET");
      command.setLocalFile (directory);

      manager.prepareTransfer (command);

      assertFalse (manager.getTransfer ().isPresent ());
    }

    @Test
    @DisplayName ("um upload com arquivo valido carrega o conteudo e abre a transferencia")
    void uploadReadsTheFile (@TempDir File directory) throws IOException
    {
      File localFile = new File (directory, "dados.txt");
      Files.write (localFile.toPath (), ascii ("CONTEUDO"));

      IndFileCommand command = new IndFileCommand ("IND$FILE PUT MEU.DATASET");
      command.setLocalFile (localFile);

      manager.prepareTransfer (command);

      assertEquals (8, command.getBuffer ().length);
      assertTrue (manager.getTransfer ().isPresent ());
      assertEquals (List.of (TransferStatus.READY), listener.events);
    }

    @Test
    @DisplayName ("um upload que ja tem buffer nao volta ao disco")
    void uploadWithBufferSkipsTheDisk ()
    {
      IndFileCommand command = new IndFileCommand ("IND$FILE PUT MEU.DATASET");
      command.setBuffer (ascii ("JA CARREGADO"));

      manager.prepareTransfer (command);

      assertEquals (12, command.getBuffer ().length);
      assertTrue (manager.getTransfer ().isPresent ());
    }

    @Test
    @DisplayName ("um download nao procura arquivo local")
    void downloadDoesNotTouchTheDisk ()
    {
      IndFileCommand command = new IndFileCommand ("IND$FILE GET MEU.DATASET");

      manager.prepareTransfer (command);

      assertTrue (manager.getTransfer ().isPresent ());
      assertEquals (List.of (TransferStatus.READY), listener.events);
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("sem transferencia ativa")
  class Idle
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("nao ha transferencia para entregar")
    void noCurrentTransfer ()
    {
      assertFalse (manager.getTransfer ().isPresent ());
    }

    @Test
    @DisplayName ("abrir sem transferencia preparada nao faz nada")
    void openWithoutTransfer ()
    {
      assertFalse (manager.openTransfer (openRecord ("FT:DATA")).isPresent ());
      assertTrue (listener.events.isEmpty ());
    }

    @Test
    @DisplayName ("fechar sem transferencia preparada nao faz nada")
    void closeWithoutTransfer ()
    {
      assertFalse (manager.closeTransfer (openRecord ("FT:DATA")).isPresent ());
      assertTrue (listener.events.isEmpty ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("ciclo de uma transferencia de dados")
  class DataTransfer
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("abrir avisa os listeners e entrega a transferencia")
    void openNotifiesListeners ()
    {
      Transfer transfer = downloadOf ("MEU.DATASET");

      assertSame (transfer, manager.openTransfer (openRecord ("FT:DATA")).get ());
      assertEquals (List.of (TransferStatus.OPEN), listener.events);
      assertTrue (transfer.isData ());
    }

    @Test
    @DisplayName ("processar um buffer de dados avisa PROCESSING")
    void processNotifiesProcessing ()
    {
      Transfer transfer = downloadOf ("MEU.DATASET");
      manager.openTransfer (openRecord ("FT:DATA"));
      listener.events.clear ();

      manager.process (dataRecord ("LINHA"));

      assertEquals (List.of (TransferStatus.PROCESSING), listener.events);
      assertSame (transfer, manager.getTransfer ().get ());
    }

    @Test
    @DisplayName ("fechar grava o arquivo, avisa FINISHED e devolve a transferencia")
    void closeWritesTheFile ()
    {
      Transfer transfer = downloadOf ("MEU.DATASET");
      manager.openTransfer (openRecord ("FT:DATA"));
      transfer.add (new DataRecord (ascii ("CONTEUDO"), 0, 8, false));
      listener.events.clear ();

      assertSame (transfer, manager.closeTransfer (openRecord ("FT:DATA")).get ());
      assertEquals (List.of (TransferStatus.FINISHED), listener.events);
      assertTrue (transfer.getFile ().exists (), transfer.getFile ().toString ());
    }

    @Test
    @DisplayName ("uma transferencia de dados sobrevive ao fechamento")
    void dataTransferSurvivesClose ()
    {
      Transfer transfer = downloadOf ("MEU.DATASET");
      manager.openTransfer (openRecord ("FT:DATA"));
      transfer.add (new DataRecord (ascii ("X"), 0, 1, false));

      manager.closeTransfer (openRecord ("FT:DATA"));

      // closeTransfer () so descarta a transferencia quando ela e uma mensagem
      assertTrue (manager.getTransfer ().isPresent ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("ciclo de uma mensagem")
  class MessageTransfer
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("processar uma mensagem nao avisa PROCESSING")
    void processDoesNotNotifyForMessages ()
    {
      downloadOf ("MEU.DATASET");
      manager.openTransfer (openRecord ("FT:MSG "));
      listener.events.clear ();

      manager.process (dataRecord ("AVISO"));

      assertTrue (listener.events.isEmpty (), "so transferencias de dados avisam");
    }

    @Test
    @DisplayName ("fechar uma mensagem descarta a transferencia")
    void closeDiscardsTheMessage ()
    {
      downloadOf ("MEU.DATASET");
      manager.openTransfer (openRecord ("FT:MSG "));
      listener.events.clear ();

      manager.closeTransfer ();

      assertEquals (List.of (TransferStatus.FINISHED), listener.events);
      assertFalse (manager.getTransfer ().isPresent ());
    }

    @Test
    @DisplayName ("fechar uma mensagem pelo caminho longo viola a assercao")
    void closingAMessageWithARecordFails ()
    {
      downloadOf ("MEU.DATASET");
      manager.openTransfer (openRecord ("FT:MSG "));

      // closeTransfer (record) exige `currentTransfer.isData ()`
      assertThrows (AssertionError.class,
                    () -> manager.closeTransfer (openRecord ("FT:MSG ")));
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("listeners e site de replay")
  class ListenersAndSite
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("o mesmo listener nao e registrado duas vezes")
    void doesNotDuplicateListeners ()
    {
      manager.addTransferListener (listener);       // ja registrado no setUp
      downloadOf ("MEU.DATASET");

      manager.openTransfer (openRecord ("FT:DATA"));

      assertEquals (1, listener.events.size ());
    }

    @Test
    @DisplayName ("um listener removido para de receber eventos")
    void removedListenerStopsReceiving ()
    {
      manager.removeTransferListener (listener);
      downloadOf ("MEU.DATASET");

      manager.openTransfer (openRecord ("FT:DATA"));

      assertTrue (listener.events.isEmpty ());
    }

    @Test
    @DisplayName ("remover um listener nunca registrado e inofensivo")
    void removingUnknownListenerIsHarmless ()
    {
      manager.removeTransferListener ( (status, transfer) ->
      {
      });
    }

    @Test
    @DisplayName ("o site de replay pode ser trocado a qualquer momento")
    void replayServerCanBeSet ()
    {
      manager.setReplayServer (NO_SITE);

      assertFalse (manager.getTransfer ().isPresent ());
    }

    @Test
    @DisplayName ("os quatro estados de transferencia estao declarados")
    void statusEnumIsComplete ()
    {
      assertEquals (4, TransferStatus.values ().length);
      assertEquals (TransferStatus.READY, TransferStatus.valueOf ("READY"));
    }
  }

  // ---------------------------------------------------------------------------------//
  private static class RecordingListener implements TransferListener
  // ---------------------------------------------------------------------------------//
  {
    private final List<TransferStatus> events = new ArrayList<> ();

    @Override
    public void transferStatusChanged (TransferStatus status, Transfer transfer)
    {
      events.add (status);
    }
  }
}
