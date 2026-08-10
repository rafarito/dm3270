package com.bytezone.dm3270.filetransfer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.bytezone.dm3270.filetransfer.Transfer.TransferContents;
import com.bytezone.dm3270.filetransfer.Transfer.TransferType;
import com.bytezone.dm3270.utilities.FileSaver;

// -----------------------------------------------------------------------------------//
@DisplayName ("Transfer - estado de uma transferencia IND$FILE")
class TransferTest
// -----------------------------------------------------------------------------------//
{
  private static byte[] bytes (int... values)
  {
    byte[] buffer = new byte[values.length];
    for (int i = 0; i < values.length; i++)
      buffer[i] = (byte) values[i];

    return buffer;
  }

  private static byte[] ascii (String text)
  {
    return text.getBytes (java.nio.charset.StandardCharsets.US_ASCII);
  }

  // Um Transfer de download, sem Site (que exige JavaFX) e sem TLQ.
  private static Transfer download (String command)
  {
    return new Transfer (new IndFileCommand (command), null, "");
  }

  private static DataRecord dataRecord (byte[] content)
  {
    return new DataRecord (content, 0, content.length, false);
  }

  // Um campo estruturado de saida que declara o conteudo da transferencia.
  private static FileTransferOutboundSF outbound (String contents)
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

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("nome do dataset")
  class DatasetName
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("o nome e sempre passado para maiusculas")
    void upperCasesName ()
    {
      assertEquals ("MEU.DATASET",
                    download ("IND$FILE GET meu.dataset").getDatasetName ());
    }

    @Test
    @DisplayName ("sem HLQ e com TLQ configurado o TLQ e prefixado")
    void prefixesTlq ()
    {
      Transfer transfer =
          new Transfer (new IndFileCommand ("IND$FILE GET DADOS"), null, "USER01");

      assertEquals ("USER01.DADOS", transfer.getDatasetName ());
    }

    @Test
    @DisplayName ("um nome entre apostrofos ja tem HLQ e nao recebe o TLQ")
    void keepsFullyQualifiedName ()
    {
      Transfer transfer =
          new Transfer (new IndFileCommand ("IND$FILE GET 'SYS1.PARMLIB'"), null,
                        "USER01");

      assertEquals ("SYS1.PARMLIB", transfer.getDatasetName ());
    }

    @Test
    @DisplayName ("sem TLQ o nome fica como veio")
    void withoutTlq ()
    {
      assertEquals ("DADOS", download ("IND$FILE GET DADOS").getDatasetName ());
    }

    @Test
    @DisplayName ("sem Site a pasta do servidor fica vazia")
    void noSiteMeansNoFolder ()
    {
      assertEquals ("", download ("IND$FILE GET DADOS").getSiteFolderName ());
    }

    @Test
    @DisplayName ("o arquivo local termina com o nome do dataset")
    void localFileEndsWithDatasetName ()
    {
      File file = download ("IND$FILE GET MEU.DATASET").getFile ();

      assertEquals ("MEU.DATASET", file.getName ());
      assertTrue (file.getPath ().contains ("dm3270"), file.getPath ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("conteudo e direcao")
  class ContentsAndDirection
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("uma transferencia nova nao e nem mensagem nem dados")
    void startsUndecided ()
    {
      Transfer transfer = download ("IND$FILE GET DADOS");

      assertFalse (transfer.isMessage ());
      assertFalse (transfer.isData ());
      assertNull (transfer.getTransferContents ());
      assertNull (transfer.getTransferType ());
      assertFalse (transfer.isDownloadAndIsData ());
    }

    @Test
    @DisplayName ("um campo FT:DATA marca a transferencia como dados de download")
    void adoptsDataFromOutboundField ()
    {
      Transfer transfer = download ("IND$FILE GET DADOS");

      transfer.add (outbound ("FT:DATA"));

      assertTrue (transfer.isData ());
      assertFalse (transfer.isMessage ());
      assertEquals (TransferContents.DATA, transfer.getTransferContents ());
      assertEquals (TransferType.DOWNLOAD, transfer.getTransferType ());
      assertTrue (transfer.isDownloadAndIsData ());
    }

    @Test
    @DisplayName ("um campo FT:MSG marca a transferencia como mensagem")
    void adoptsMessageFromOutboundField ()
    {
      Transfer transfer = download ("IND$FILE GET DADOS");

      transfer.add (outbound ("FT:MSG "));

      assertTrue (transfer.isMessage ());
      assertFalse (transfer.isDownloadAndIsData ());
    }

    @Test
    @DisplayName ("a direcao e definida apenas na primeira vez")
    void directionIsSetOnlyOnce ()
    {
      Transfer transfer = download ("IND$FILE GET DADOS");

      transfer.add (outbound ("FT:DATA"));            // DOWNLOAD
      transfer.add (outbound ("FT:MSG "));            // tambem DOWNLOAD

      assertEquals (TransferType.DOWNLOAD, transfer.getTransferType ());
      // o conteudo, ao contrario, e sobrescrito
      assertEquals (TransferContents.MSG, transfer.getTransferContents ());
    }

    @Test
    @DisplayName ("uma transferencia nunca se declara cancelada")
    void neverCancelled ()
    {
      // cancelled () devolve false fixo: nao ha caminho de cancelamento implementado
      assertFalse (download ("IND$FILE GET DADOS").cancelled ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("acumulacao de buffers de dados")
  class DataBuffers
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("cada buffer novo recebe o proximo numero")
    void numbersBuffersInOrder ()
    {
      Transfer transfer = download ("IND$FILE GET DADOS");
      transfer.add (outbound ("FT:DATA"));

      assertEquals (1, transfer.add (dataRecord (ascii ("AAA"))));
      assertEquals (2, transfer.add (dataRecord (ascii ("BBB"))));
      assertEquals (3, transfer.add (dataRecord (ascii ("CCC"))));
      assertEquals (3, transfer.size ());
      assertEquals (9, transfer.getDataLength ());
    }

    @Test
    @DisplayName ("reenviar o mesmo buffer devolve o numero original")
    void duplicateBufferKeepsNumber ()
    {
      Transfer transfer = download ("IND$FILE GET DADOS");
      transfer.add (outbound ("FT:DATA"));

      DataRecord first = dataRecord (ascii ("AAA"));
      DataRecord second = dataRecord (ascii ("BBB"));

      transfer.add (first);
      transfer.add (second);

      assertEquals (1, transfer.add (first));
      assertEquals (2, transfer.size (), "o buffer repetido nao deveria ser somado");
      assertEquals (6, transfer.getDataLength ());
    }

    @Test
    @DisplayName ("numa mensagem o buffer substitui o anterior e sempre vale 1")
    void messageKeepsOnlyTheLastBuffer ()
    {
      Transfer transfer = download ("IND$FILE GET DADOS");
      transfer.add (outbound ("FT:MSG "));

      assertEquals (1, transfer.add (dataRecord (ascii ("PRIMEIRA MENSAGEM "))));
      assertEquals (1, transfer.add (dataRecord (ascii ("SEGUNDA MENSAGEM "))));
      assertEquals (0, transfer.size ());
      assertEquals ("SEGUNDA MENSAGEM ", transfer.getMessage ());
    }

    @Test
    @DisplayName ("uma transferencia de dados nao tem mensagem")
    void dataTransferHasNoMessage ()
    {
      Transfer transfer = download ("IND$FILE GET DADOS");
      transfer.add (outbound ("FT:DATA"));
      transfer.add (dataRecord (ascii ("AAA")));

      assertEquals ("", transfer.getMessage ());
    }

    @Test
    @DisplayName ("uma mensagem sem buffer devolve texto vazio")
    void messageWithoutBuffer ()
    {
      Transfer transfer = download ("IND$FILE GET DADOS");
      transfer.add (outbound ("FT:MSG "));

      assertEquals ("", transfer.getMessage ());
    }

    @Test
    @DisplayName ("combineDataBuffers concatena os buffers na ordem recebida")
    void combinesBuffers ()
    {
      Transfer transfer = download ("IND$FILE GET DADOS");
      transfer.add (outbound ("FT:DATA"));
      transfer.add (dataRecord (ascii ("ABC")));
      transfer.add (dataRecord (ascii ("DEF")));

      assertArrayEquals (ascii ("ABCDEF"), transfer.combineDataBuffers ());
    }

    @Test
    @DisplayName ("sem buffers o resultado e um array vazio")
    void combinesNothing ()
    {
      Transfer transfer = download ("IND$FILE GET DADOS");

      assertEquals (0, transfer.combineDataBuffers ().length);
    }

    @Test
    @DisplayName ("com ASCII e CRLF o ultimo byte 0x1A e descartado")
    void dropsEofMarkerForAsciiCrlf ()
    {
      Transfer transfer = download ("IND$FILE GET DADOS ASCII CRLF");
      transfer.add (outbound ("FT:DATA"));
      transfer.add (dataRecord (bytes (0x41, 0x0D, 0x0A, 0x1A)));

      assertArrayEquals (bytes (0x41, 0x0D, 0x0A), transfer.combineDataBuffers ());
    }

    @Test
    @DisplayName ("descartar o 0x1A exige que o buffer termine em CR LF")
    void eofMarkerRequiresCrLfBeforeIt ()
    {
      Transfer transfer = download ("IND$FILE GET DADOS ASCII CRLF");
      transfer.add (outbound ("FT:DATA"));
      transfer.add (dataRecord (ascii ("ABCD")));

      // as assercoes do metodo exigem 0x0D 0x0A imediatamente antes do byte cortado
      assertThrowsAssertion (transfer);
    }

    private void assertThrowsAssertion (Transfer transfer)
    {
      try
      {
        transfer.combineDataBuffers ();
        org.junit.jupiter.api.Assertions.fail ("esperava AssertionError");
      }
      catch (AssertionError expected)
      {
        // o metodo confia que o mainframe sempre manda CR LF 1A no fim
      }
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("upload")
  class Upload
  // ---------------------------------------------------------------------------------//
  {
    private Transfer upload (byte[] content)
    {
      IndFileCommand command = new IndFileCommand ("IND$FILE PUT DADOS");
      command.setBuffer (content);

      return new Transfer (command, null, "");
    }

    @Test
    @DisplayName ("um download nao tem bytes a enviar")
    void downloadHasNothingToSend ()
    {
      Transfer transfer = download ("IND$FILE GET DADOS");

      assertFalse (transfer.hasMoreData ());
      assertEquals (0, transfer.getBytesLeft ());
    }

    @Test
    @DisplayName ("o conteudo do arquivo local fica pendente de envio")
    void countsPendingBytes ()
    {
      Transfer transfer = upload (ascii ("CONTEUDO DO ARQUIVO"));

      assertTrue (transfer.hasMoreData ());
      assertEquals (19, transfer.getBytesLeft ());
    }

    @Test
    @DisplayName ("cada cabecalho consumido reduz o que falta enviar")
    void headerConsumesBytes ()
    {
      Transfer transfer = upload (ascii ("0123456789"));

      DataRecord header = transfer.getDataHeader ();

      assertEquals (10, header.getBufferLength ());
      assertEquals (0, transfer.getBytesLeft ());
      assertFalse (transfer.hasMoreData ());
      assertEquals (1, transfer.size ());
    }

    @Test
    @DisplayName ("um arquivo maior que 2048 bytes vai em varios cabecalhos")
    void splitsLargeUploads ()
    {
      Transfer transfer = upload (new byte[5000]);

      assertEquals (2048, transfer.getDataHeader ().getBufferLength ());
      assertEquals (2048, transfer.getDataHeader ().getBufferLength ());
      assertEquals (904, transfer.getDataHeader ().getBufferLength ());
      assertFalse (transfer.hasMoreData ());
      assertEquals (3, transfer.size ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("toString")
  class ToString
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("um download de dados lista os buffers recebidos")
    void listsBuffers ()
    {
      Transfer transfer = download ("IND$FILE GET DADOS");
      transfer.add (outbound ("FT:DATA"));
      transfer.add (dataRecord (ascii ("ABC")));
      transfer.add (dataRecord (ascii ("DEFGH")));

      String report = transfer.toString ();

      assertTrue (report.contains ("Contents ....... DATA"), report);
      assertTrue (report.contains ("Type ........... DOWNLOAD"), report);
      assertTrue (report.contains ("Dataset name ... DADOS"), report);
      assertTrue (report.contains ("Buffer   0"), report);
      assertTrue (report.contains ("Buffer   1"), report);
      assertFalse (report.contains ("inbuf length"), report);
    }

    @Test
    @DisplayName ("uma mensagem mostra o texto em vez dos buffers")
    void showsMessage ()
    {
      Transfer transfer = download ("IND$FILE GET DADOS");
      transfer.add (outbound ("FT:MSG "));
      transfer.add (dataRecord (ascii ("TRANSFERENCIA CONCLUIDA ")));

      String report = transfer.toString ();

      assertTrue (report.contains ("Message ........ TRANSFERENCIA CONCLUIDA"), report);
      assertFalse (report.contains ("Data length"), report);
    }

    @Test
    @DisplayName ("um upload mostra o tamanho e a posicao do buffer de entrada")
    void showsInboundBuffer ()
    {
      IndFileCommand command = new IndFileCommand ("IND$FILE PUT DADOS");
      command.setBuffer (ascii ("0123456789"));
      Transfer transfer = new Transfer (command, null, "");

      transfer.getDataHeader ();
      String report = transfer.toString ();

      assertTrue (report.contains ("inbuf length ... 10"), report);
      assertTrue (report.contains ("in ptr ......... 10"), report);
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("FileSaver - onde o dataset e gravado")
  class SaveLocation
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("o caminho base fica sob ~/dm3270/files")
    void buildsHomePath ()
    {
      Path path = FileSaver.getHomePath ("SITE01");

      assertEquals ("SITE01", path.getFileName ().toString ());
      assertTrue (path.toString ().contains ("dm3270"), path.toString ());
      assertTrue (path.toString ().contains ("files"), path.toString ());
    }

    @Test
    @DisplayName ("um Site nulo nao produz caminho")
    void nullSiteGivesNoPath ()
    {
      assertNull (FileSaver.getHomePath ((com.bytezone.dm3270.utilities.Site) null));
    }

    @ParameterizedTest (name = "{0} -> {1} segmentos")
    @CsvSource ({ "MEU.DATASET, 2", "SYS1.PARMLIB.MEMBRO, 3", "SIMPLES, 1" })
    @DisplayName ("o nome do dataset se transforma numa lista de pastas")
    void splitsIntoSegments (String datasetName, int expected)
    {
      assertEquals (expected, FileSaver.getSegments (datasetName).length);
    }

    @Test
    @DisplayName ("o nome do membro de um PDS e removido do ultimo segmento")
    void stripsMemberName ()
    {
      String[] segments = FileSaver.getSegments ("SYS1.PROCLIB(IEFBR14)");

      assertArrayEquals (new String[] { "SYS1", "PROCLIB" }, segments);
    }

    @Test
    @DisplayName ("sem nenhuma pasta criada o caminho de gravacao e a raiz")
    void fallsBackToHomePath (@TempDir File directory)
    {
      Path home = directory.toPath ();

      assertEquals (home.toString (),
                    FileSaver.getSaveFolderName (home, "MEU.DATASET"));
    }

    @Test
    @DisplayName ("cada pasta existente aprofunda o caminho de gravacao")
    void followsExistingFolders (@TempDir File directory) throws IOException
    {
      Path home = directory.toPath ();
      Files.createDirectories (home.resolve ("SYS1").resolve ("PARMLIB"));

      assertEquals (Paths.get (home.toString (), "SYS1", "PARMLIB").toString (),
                    FileSaver.getSaveFolderName (home, "SYS1.PARMLIB"));
    }

    @Test
    @DisplayName ("um arquivo ja existente para a busca naquela pasta")
    void stopsWhereTheFileAlreadyIs (@TempDir File directory) throws IOException
    {
      Path home = directory.toPath ();
      Path sys1 = home.resolve ("SYS1");
      Files.createDirectories (sys1.resolve ("PARMLIB"));
      Files.write (sys1.resolve ("SYS1.PARMLIB"), new byte[1]);

      assertEquals (sys1.toString (),
                    FileSaver.getSaveFolderName (home, "SYS1.PARMLIB"));
    }
  }
}
