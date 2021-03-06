package org.devtcg.robotrc.robotdata.model

data class AttributeValueLocal(
  val type: String,
  val valueAsString: String,
  val source: AttributeValueSource = AttributeValueSource.LOCAL_WRITE,
) {
  fun coerceToString() = valueAsString

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

  fun asStringList(): List<String> {
    return when (type) {
      "[string]" -> valueAsString.split(' ')
      else -> throw AttributeValueException(toString())
    }
  }
}
