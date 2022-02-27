package org.devtcg.robotrc.devicewidget.api

data class AttributeValueLocal(
  val type: String,
  val valueAsString: String,
) {
  fun asNumber(): Number {
    try {
      return when {
        type.startsWith("uint") -> valueAsString.toLong()
        type.startsWith("int") -> valueAsString.toLong()
        type.startsWith("float") || type.startsWith("double") -> {
          valueAsString.toDouble()
        }
        else -> throw AttributeValueException(toString())
      }
    } catch (e: NumberFormatException) {
      throw AttributeValueException("${toString()}: $e")
    }
  }

  fun asString(): String {
    return when (type) {
      "string" -> valueAsString
      else -> throw AttributeValueException(toString())
    }
  }
}