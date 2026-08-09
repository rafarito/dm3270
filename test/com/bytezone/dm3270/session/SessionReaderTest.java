package com.bytezone.dm3270.session;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.bytezone.dm3270.streams.TelnetSocket.Source;

// -----------------------------------------------------------------------------------//
@DisplayName ("SessionReader - leitura de um arquivo de replay")
class SessionReaderTest
// -----------------------------------------------------------------------------------//
{
  // Um registro do arquivo tem uma linha de cabecalho (Server/Client, marca de MITM na
  // coluna 7 e data a partir da coluna 9) seguida das linhas do dump hexadecimal.
  private static final String SERVER = "Server   2024-01-15T10:30:00";
  private static final String CLIENT = "Client   2024-01-15T10:30:01";

  private static byte[] bytes (int... values)
  {
    byte[] buffer = new byte[values.length];
    for (int i = 0; i < values.length; i++)
      buffer[i] = (byte) values[i];

    return buffer;
  }

  // Uma linha de dump com offset de 4 digitos, como a produzida por Dm3270Utility.toHex.
  private static String dump (int offset, int... values)
  {
    StringBuilder hex = new StringBuilder ();
    StringBuilder text = new StringBuilder ();
    for (int value : values)
    {
      hex.append (String.format ("%02X ", value));
      text.append (value < 0x40 ? '.' : 'x');
    }

    return String.format ("%04X  %-48s %s", offset, hex.toString (), text.toString ());
  }

  // A mesma linha com offset de 6 digitos: o leitor descobre o recuo sozinho.
  private static String wideDump (int offset, int... values)
  {
    StringBuilder hex = new StringBuilder ();
    for (int value : values)
      hex.append (String.format ("%02X ", value));

    return String.format ("%06X  %-48s %s", offset, hex.toString (), "texto");
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("leitura de buffers")
  class Buffers
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("le um buffer de uma unica linha")
    void readsSingleLine () throws Exception
    {
      SessionReader reader = new SessionReader (Source.SERVER,
          Arrays.asList (SERVER, dump (0, 0xFF, 0xFD, 0x28)));

      assertArrayEquals (bytes (0xFF, 0xFD, 0x28), reader.nextBuffer ());
    }

    @Test
    @DisplayName ("junta as linhas de um buffer maior que 16 bytes")
    void joinsSeveralLines () throws Exception
    {
      int[] first = new int[16];
      Arrays.fill (first, 0x41);

      SessionReader reader = new SessionReader (Source.SERVER,
          Arrays.asList (SERVER, dump (0, first), dump (16, 0x42, 0x43)));

      byte[] buffer = reader.nextBuffer ();

      assertEquals (18, buffer.length);
      assertEquals (0x41, buffer[15]);
      assertEquals (0x42, buffer[16]);
      assertEquals (0x43, buffer[17]);
    }

    @Test
    @DisplayName ("aceita offsets de seis digitos")
    void readsWideOffsets () throws Exception
    {
      SessionReader reader = new SessionReader (Source.SERVER,
          Arrays.asList (SERVER, wideDump (0, 0xFF, 0xEF)));

      assertArrayEquals (bytes (0xFF, 0xEF), reader.nextBuffer ());
    }

    @Test
    @DisplayName ("le os buffers em sequencia")
    void readsSuccessiveBuffers () throws Exception
    {
      SessionReader reader = new SessionReader (Source.SERVER,
          Arrays.asList (SERVER, dump (0, 0x01), SERVER, dump (0, 0x02, 0x03),
                         SERVER, dump (0, 0x04)));

      assertArrayEquals (bytes (0x01), reader.nextBuffer ());
      assertArrayEquals (bytes (0x02, 0x03), reader.nextBuffer ());
      assertArrayEquals (bytes (0x04), reader.nextBuffer ());
      assertEquals (0, reader.next ());
    }

    @Test
    @DisplayName ("uma linha em branco encerra o buffer")
    void blankLineEndsBuffer () throws Exception
    {
      SessionReader reader = new SessionReader (Source.SERVER,
          Arrays.asList (SERVER, dump (0, 0x01), "", dump (16, 0x02)));

      assertArrayEquals (bytes (0x01), reader.nextBuffer ());
    }

    @Test
    @DisplayName ("uma lista vazia nao tem buffer nenhum")
    void emptyInput () throws Exception
    {
      SessionReader reader = new SessionReader (Source.SERVER, Arrays.asList ());

      assertEquals (0, reader.next ());
      assertEquals (0, reader.nextBuffer ().length);
    }

    @Test
    @DisplayName ("um arquivo sem cabecalho do nosso lado nao rende buffers")
    void noMatchingHeader () throws Exception
    {
      SessionReader reader = new SessionReader (Source.SERVER,
          Arrays.asList (CLIENT, dump (0, 0x01)));

      assertEquals (0, reader.next ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("separacao entre cliente e servidor")
  class Sides
  // ---------------------------------------------------------------------------------//
  {
    private final List<String> conversation =
        Arrays.asList (SERVER, dump (0, 0xFF, 0xFD, 0x28),
                       CLIENT, dump (0, 0xFF, 0xFB, 0x28),
                       SERVER, dump (0, 0xFF, 0xFD, 0x19));

    @Test
    @DisplayName ("o leitor do servidor ve apenas o que o servidor mandou")
    void serverSeesServerBuffers () throws Exception
    {
      SessionReader reader = new SessionReader (Source.SERVER, conversation);

      assertArrayEquals (bytes (0xFF, 0xFD, 0x28), reader.nextBuffer ());
      assertArrayEquals (bytes (0xFF, 0xFD, 0x19), reader.nextBuffer ());
      assertEquals (0, reader.next ());
    }

    @Test
    @DisplayName ("o leitor do cliente ve apenas o que o cliente mandou")
    void clientSeesClientBuffers () throws Exception
    {
      SessionReader reader = new SessionReader (Source.CLIENT, conversation);

      assertArrayEquals (bytes (0xFF, 0xFB, 0x28), reader.nextBuffer ());
      assertEquals (0, reader.next ());
    }

    @Test
    @DisplayName ("nextLineNo avanca conforme o arquivo e consumido")
    void tracksPosition () throws Exception
    {
      SessionReader reader = new SessionReader (Source.SERVER, conversation);

      int start = reader.nextLineNo ();
      reader.nextBuffer ();

      assertTrue (reader.nextLineNo () > start,
                  "a posicao deveria avancar depois de ler um buffer");
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("metadados do registro")
  class Metadata
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("le a data e hora do cabecalho")
    void readsDateTime () throws Exception
    {
      SessionReader reader = new SessionReader (Source.SERVER,
          Arrays.asList (SERVER, dump (0, 0x01)));

      reader.next ();

      assertEquals (LocalDateTime.parse ("2024-01-15T10:30:00"), reader.getDateTime ());
    }

    @Test
    @DisplayName ("um cabecalho sem marca na coluna 7 e considerado genuino")
    void defaultsToGenuine () throws Exception
    {
      SessionReader reader = new SessionReader (Source.SERVER,
          Arrays.asList (SERVER, dump (0, 0x01)));

      reader.next ();

      assertTrue (reader.isGenuine ());
    }

    @Test
    @DisplayName ("um asterisco na coluna 7 marca o registro como fabricado")
    void asteriskMeansMitm () throws Exception
    {
      SessionReader reader = new SessionReader (Source.SERVER,
          Arrays.asList ("Server *  2024-01-15T10:30:00", dump (0, 0x01)));

      reader.next ();

      assertFalse (reader.isGenuine ());
    }

    @Test
    @DisplayName ("um cabecalho curto nao tem data e vale como genuino")
    void shortHeader () throws Exception
    {
      SessionReader reader =
          new SessionReader (Source.SERVER, Arrays.asList ("Server", dump (0, 0x01)));

      reader.next ();

      assertTrue (reader.isGenuine ());
      assertNull (reader.getDateTime ());
    }

    @Test
    @DisplayName ("uma linha ## define o rotulo dos registros seguintes")
    void readsLabel () throws Exception
    {
      SessionReader reader = new SessionReader (Source.SERVER,
          Arrays.asList ("## Negociacao inicial", SERVER, dump (0, 0x01)));

      reader.next ();

      assertEquals ("Negociacao inicial", reader.getLabel ());
    }

    @Test
    @DisplayName ("sem linha ## o rotulo fica nulo")
    void noLabel () throws Exception
    {
      SessionReader reader = new SessionReader (Source.SERVER,
          Arrays.asList (SERVER, dump (0, 0x01)));

      reader.next ();

      assertNull (reader.getLabel ());
    }

    @Test
    @DisplayName ("os metadados acompanham o buffer devolvido, nao o proximo")
    void metadataMatchesTheBufferJustRead () throws Exception
    {
      SessionReader reader = new SessionReader (Source.SERVER,
          Arrays.asList ("## primeiro", SERVER, dump (0, 0x01),
                         "## segundo", "Server *  2024-01-15T11:00:00", dump (0, 0x02)));

      reader.nextBuffer ();

      assertEquals ("primeiro", reader.getLabel ());
      assertTrue (reader.isGenuine ());

      reader.nextBuffer ();

      assertEquals ("segundo", reader.getLabel ());
      assertFalse (reader.isGenuine ());
      assertEquals (LocalDateTime.parse ("2024-01-15T11:00:00"), reader.getDateTime ());
    }

    @Test
    @DisplayName ("uma data invalida no cabecalho interrompe a leitura")
    void invalidDateTime ()
    {
      // o parse acontece dentro do construtor, ao posicionar no primeiro registro
      assertThrows (java.time.format.DateTimeParseException.class,
                    () -> new SessionReader (Source.SERVER,
                                             Arrays.asList ("Server   15/01/2024",
                                                            dump (0, 0x01))));
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("leitura de arquivo")
  class Files_
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("le a sessao de um arquivo em disco")
    void readsFromDisk (@TempDir File directory) throws Exception
    {
      Path path = new File (directory, "sessao.txt").toPath ();
      Files.write (path, Arrays.asList (SERVER, dump (0, 0xFF, 0xFD, 0x28),
                                        CLIENT, dump (0, 0xFF, 0xFB, 0x28)));

      SessionReader server = new SessionReader (Source.SERVER, path);
      SessionReader client = new SessionReader (Source.CLIENT, path);

      assertArrayEquals (bytes (0xFF, 0xFD, 0x28), server.nextBuffer ());
      assertArrayEquals (bytes (0xFF, 0xFB, 0x28), client.nextBuffer ());
    }

    @Test
    @DisplayName ("um arquivo inexistente resulta numa sessao vazia")
    void missingFile (@TempDir File directory) throws Exception
    {
      Path path = new File (directory, "nao-existe.txt").toPath ();

      SessionReader reader = new SessionReader (Source.SERVER, path);

      assertEquals (0, reader.next ());
    }

    @Test
    @DisplayName ("um arquivo vazio tambem")
    void emptyFile (@TempDir File directory) throws Exception
    {
      Path path = new File (directory, "vazio.txt").toPath ();
      Files.write (path, new byte[0]);

      assertEquals (0, new SessionReader (Source.SERVER, path).next ());
    }
  }
}
