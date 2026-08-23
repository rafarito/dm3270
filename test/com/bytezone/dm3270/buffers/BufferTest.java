package com.bytezone.dm3270.buffers;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.bytezone.dm3270.display.ScreenTarget;

// -----------------------------------------------------------------------------------//
@DisplayName ("Buffers - encapsulamento telnet dos dados 3270")
class BufferTest
// -----------------------------------------------------------------------------------//
{
  private static final byte IAC = (byte) 0xFF;
  private static final byte EOR = (byte) 0xEF;

  // stub concreto: AbstractBuffer e abstrato apenas por causa de process()
  // ---------------------------------------------------------------------------------//
  private static class TestBuffer extends AbstractBuffer
  // ---------------------------------------------------------------------------------//
  {
    TestBuffer (byte[] buffer)
    {
      super (buffer);
    }

    TestBuffer (byte[] buffer, int offset, int length)
    {
      super (buffer, offset, length);
    }

    @Override
    public void process (ScreenTarget screen)
    {
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("AbstractBuffer")
  class Abstract
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("copia o buffer de origem (nao guarda a referencia)")
    void copiesSourceBuffer ()
    {
      byte[] source = { 0x01, 0x02, 0x03 };
      TestBuffer buffer = new TestBuffer (source);

      source[0] = 0x7F;

      assertEquals (0x01, buffer.getData ()[0], "alterar a origem nao pode afetar o buffer");
      assertEquals (3, buffer.size ());
    }

    @Test
    @DisplayName ("copia apenas o trecho indicado por offset/length")
    void copiesSlice ()
    {
      byte[] source = { 0x01, 0x02, 0x03, 0x04, 0x05 };

      TestBuffer buffer = new TestBuffer (source, 1, 3);

      assertArrayEquals (new byte[] { 0x02, 0x03, 0x04 }, buffer.getData ());
    }

    @Test
    @DisplayName ("getTelnetData anexa IAC EOR ao final")
    void appendsIacEor ()
    {
      TestBuffer buffer = new TestBuffer (new byte[] { 0x01, 0x02 });

      assertArrayEquals (new byte[] { 0x01, 0x02, IAC, EOR }, buffer.getTelnetData ());
    }

    @Test
    @DisplayName ("getTelnetData duplica cada 0xFF de dados (escape IAC)")
    void doublesDataFF ()
    {
      TestBuffer buffer = new TestBuffer (new byte[] { 0x01, IAC, 0x02 });

      assertArrayEquals (new byte[] { 0x01, IAC, IAC, 0x02, IAC, EOR },
          buffer.getTelnetData ());
    }

    @Test
    @DisplayName ("duplica varios 0xFF consecutivos")
    void doublesConsecutiveFF ()
    {
      TestBuffer buffer = new TestBuffer (new byte[] { IAC, IAC });

      assertArrayEquals (new byte[] { IAC, IAC, IAC, IAC, IAC, EOR },
          buffer.getTelnetData ());
    }

    @Test
    @DisplayName ("buffer vazio gera apenas IAC EOR")
    void emptyBuffer ()
    {
      assertArrayEquals (new byte[] { IAC, EOR },
          new TestBuffer (new byte[0]).getTelnetData ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("MultiBuffer")
  class Multi
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("soma o tamanho dos buffers agregados")
    void sumsSizes ()
    {
      MultiBuffer multi = new MultiBuffer ();
      multi.addBuffer (new TestBuffer (new byte[] { 0x01, 0x02 }));
      multi.addBuffer (new TestBuffer (new byte[] { 0x03 }));

      assertEquals (3, multi.size ());
      assertEquals (2, multi.totalBuffers ());
    }

    @Test
    @DisplayName ("concatena os dados na ordem de insercao")
    void concatenatesData ()
    {
      MultiBuffer multi = new MultiBuffer ();
      multi.addBuffer (new TestBuffer (new byte[] { 0x01, 0x02 }));
      multi.addBuffer (new TestBuffer (new byte[] { 0x03, 0x04 }));

      assertArrayEquals (new byte[] { 0x01, 0x02, 0x03, 0x04 }, multi.getData ());
    }

    @Test
    @DisplayName ("getTelnetData encapsula cada buffer separadamente")
    void wrapsEachBufferIndividually ()
    {
      MultiBuffer multi = new MultiBuffer ();
      multi.addBuffer (new TestBuffer (new byte[] { 0x01 }));
      multi.addBuffer (new TestBuffer (new byte[] { 0x02 }));

      assertArrayEquals (new byte[] { 0x01, IAC, EOR, 0x02, IAC, EOR },
          multi.getTelnetData ());
    }

    @Test
    @DisplayName ("permite recuperar cada buffer pelo indice")
    void retrievesByIndex ()
    {
      MultiBuffer multi = new MultiBuffer ();
      Buffer first = new TestBuffer (new byte[] { 0x01 });
      multi.addBuffer (first);

      assertEquals (first, multi.getBuffer (0));
    }

    @Test
    @DisplayName ("multibuffer vazio tem tamanho zero")
    void emptyMultiBuffer ()
    {
      MultiBuffer multi = new MultiBuffer ();

      assertEquals (0, multi.size ());
      assertEquals (0, multi.totalBuffers ());
      assertArrayEquals (new byte[0], multi.getTelnetData ());
    }
  }
}
