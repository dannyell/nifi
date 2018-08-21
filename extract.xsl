<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xs="http://www.w3.org/2001/XMLSchema" exclude-result-prefixes="xs">
    <xsl:output omit-xml-declaration="yes"/>
    <xsl:variable name="ws" select="' '"/>
    <!-- Get text. Replace all “break with white space“ -->
    <xsl:variable name="linebreak">
        <xsl:text>
</xsl:text>
    </xsl:variable>
  
    <!-- Result template -->
    <xsl:template match="*">
        <article><xsl:value-of select="$linebreak"/>
            <doi><xsl:value-of select="normalize-space(/article/front/article-meta/article-id[@pub-id-type='doi'])"/></doi><xsl:value-of select="$linebreak"/>
            <title><xsl:value-of select="normalize-space(/article/front[1]/article-meta[1]/title-group[1]/article-title[1])"/></title><xsl:value-of select="$linebreak"/>
            <abstract><xsl:value-of select="normalize-space(/article/front[1]/article-meta[1]/abstract[1])"/></abstract><xsl:value-of select="$linebreak"/>
            <content><xsl:value-of select="normalize-space(/article/body[1])"/></content><xsl:value-of select="$linebreak"/>
        </article><xsl:value-of select="$linebreak"/>
    </xsl:template>
</xsl:stylesheet>
