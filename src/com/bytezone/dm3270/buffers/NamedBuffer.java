package com.bytezone.dm3270.buffers;

/**
 * Buffer que expoe um nome exibivel (usado por SessionRecord). CommandHeader e
 * StructuredField nao a implementam propositalmente por nao terem nome proprio.
 */
// -----------------------------------------------------------------------------------//
public interface NamedBuffer extends Buffer
// -----------------------------------------------------------------------------------//
{
  String getName ();
}
