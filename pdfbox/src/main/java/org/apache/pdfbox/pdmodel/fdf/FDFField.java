/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.pdfbox.pdmodel.fdf;

import java.io.IOException;
import java.io.Writer;

import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.cos.COSString;

import org.apache.pdfbox.pdmodel.common.COSObjectable;
import org.apache.pdfbox.pdmodel.common.COSArrayList;
import org.apache.pdfbox.pdmodel.common.PDTextStream;

import org.apache.pdfbox.pdmodel.interactive.action.PDActionFactory;
import org.apache.pdfbox.pdmodel.interactive.action.PDAdditionalActions;
import org.apache.pdfbox.pdmodel.interactive.action.type.PDAction;

import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary;

import org.apache.pdfbox.util.XMLUtil;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * This represents an FDF field that is part of the FDF document.
 *
 * @author <a href="mailto:ben@benlitchfield.com">Ben Litchfield</a>
 * @version $Revision: 1.5 $
 */
public class FDFField implements COSObjectable
{
    private COSDictionary field;

    /**
     * Default constructor.
     */
    public FDFField()
    {
        field = new COSDictionary();
    }

    /**
     * Constructor.
     *
     * @param f The FDF field.
     */
    public FDFField( COSDictionary f )
    {
        field = f;
    }

    /**
     * This will create an FDF field from an XFDF XML document.
     *
     * @param fieldXML The XML document that contains the XFDF data.
     * @throws IOException If there is an error reading from the dom.
     */
    public FDFField( Element fieldXML ) throws IOException
    {
        this();
        this.setPartialFieldName( fieldXML.getAttribute( "name" ) );
        NodeList nodeList = fieldXML.getChildNodes();
        List kids = new ArrayList();
        for( int i=0; i<nodeList.getLength(); i++ )
        {
            Node node = nodeList.item( i );
            if( node instanceof Element )
            {
                Element child = (Element)node;
                if( child.getTagName().equals( "value" ) )
                {
                    setValue( XMLUtil.getNodeValue( child ) );
                }
                else if( child.getTagName().equals( "value-richtext" ) )
                {
                    setRichText( new PDTextStream( XMLUtil.getNodeValue( child ) ) );
                }
                else if( child.getTagName().equals( "field" ) )
                {
                    kids.add( new FDFField( child ) );
                }
            }
        }
        if( kids.size() > 0 )
        {
            setKids( kids );
        }

    }

    /**
     * This will write this element as an XML document.
     *
     * @param output The stream to write the xml to.
     *
     * @throws IOException If there is an error writing the XML.
     */
    public void writeXML( Writer output ) throws IOException
    {
        output.write( "<field name=\"" + getPartialFieldName() + "\">\n");
        Object value = getValue();
        if( value != null )
        {
            output.write( "<value>" + value + "</value>\n" );
        }
        PDTextStream rt = getRichText();
        if( rt != null )
        {
            output.write( "<value-richtext>" + rt.getAsString() + "</value-richtext>\n" );
        }
        List kids = getKids();
        if( kids != null )
        {
            for( int i=0; i<kids.size(); i++ )
            {
                ((FDFField)kids.get( i ) ).writeXML( output );
            }
        }
        output.write( "</field>\n");
    }

    /**
     * Convert this standard java object to a COS object.
     *
     * @return The cos object that matches this Java object.
     */
    public COSBase getCOSObject()
    {
        return field;
    }

    /**
     * Convert this standard java object to a COS object.
     *
     * @return The cos object that matches this Java object.
     */
    public COSDictionary getCOSDictionary()
    {
        return field;
    }

    /**
     * This will get the list of kids.  This will return a list of FDFField objects.
     * This will return null if the underlying list is null.
     *
     * @return The list of kids.
     */
    public List getKids()
    {
        COSArray kids = (COSArray)field.getDictionaryObject( "Kids" );
        List retval = null;
        if( kids != null )
        {
            List actuals = new ArrayList();
            for( int i=0; i<kids.size(); i++ )
            {
                actuals.add( new FDFField( (COSDictionary)kids.getObject( i ) ) );
            }
            retval = new COSArrayList( actuals, kids );
        }
        return retval;
    }

    /**
     * This will set the list of kids.
     *
     * @param kids A list of FDFField objects.
     */
    public void setKids( List kids )
    {
        field.setItem( "Kids", COSArrayList.converterToCOSArray( kids ) );
    }

    /**
     * This will get the "T" entry in the field dictionary.  A partial field
     * name.  Where the fully qualified field name is a concatenation of
     * the parent's fully qualified field name and "." as a separator.  For example<br/>
     * Address.State<br />
     * Address.City<br />
     *
     * @return The partial field name.
     */
    public String getPartialFieldName()
    {
        return field.getString( "T" );
    }

    /**
     * This will set the partial field name.
     *
     * @param partial The partial field name.
     */
    public void setPartialFieldName( String partial )
    {
        field.setString( "T", partial );
    }

    /**
     * This will set the value for the field.  This will return type will either be <br />
     * String : Checkboxes, Radio Button <br />
     * java.util.List of strings: Choice Field
     * PDTextStream: Textfields
     *
     * @return The value of the field.
     *
     * @throws IOException If there is an error getting the value.
     */
    public Object getValue() throws IOException
    {
        Object retval = null;
        COSBase value = field.getDictionaryObject( "V" );
        if( value instanceof COSName )
        {
            retval = ((COSName)value).getName();
        }
        else if( value instanceof COSArray )
        {
            retval = COSArrayList.convertCOSStringCOSArrayToList( (COSArray)value );
        }
        else if( value instanceof COSString || value instanceof COSStream )
        {
            retval = PDTextStream.createTextStream( value );
        }
        else if( value == null )
        {
            //Ok, value is null so do nothing
        }
        else
        {
            throw new IOException( "Error:Unknown type for field import" + value );
        }
        return retval;
    }

    /**
     * You should pass in a string, or a java.util.List of strings to set the
     * value.
     *
     * @param value The value that should populate when imported.
     *
     * @throws IOException If there is an error setting the value.
     */
    public void setValue( Object value ) throws IOException
    {
        COSBase cos = null;
        if( value instanceof List )
        {
            cos = COSArrayList.convertStringListToCOSStringCOSArray( (List)value );
        }
        else if( value instanceof String )
        {
            cos = COSName.getPDFName( (String)value );
        }
        else if( value instanceof COSObjectable )
        {
            cos = ((COSObjectable)value).getCOSObject();
        }
        else if( value == null )
        {
            //do nothing and let cos remain null as well.
        }
        else
        {
            throw new IOException( "Error:Unknown type for field import" + value );
        }
        field.setItem( "V", cos );
    }

    /**
     * This will get the Ff entry of the cos dictionary.  If it it not present then
     * this method will return null.
     *
     * @return The field flags.
     */
    public Integer getFieldFlags()
    {
        Integer retval = null;
        COSNumber ff = (COSNumber)field.getDictionaryObject( "Ff" );
        if( ff != null )
        {
            retval = new Integer( ff.intValue() );
        }
        return retval;
    }

    /**
     * This will get the field flags that are associated with this field.  The Ff entry
     * in the FDF field dictionary.
     *
     * @param ff The new value for the field flags.
     */
    public void setFieldFlags( Integer ff )
    {
        COSInteger value = null;
        if( ff != null )
        {
            value = COSInteger.get( ff );
        }
        field.setItem( "Ff", value );
    }

    /**
     * This will get the field flags that are associated with this field.  The Ff entry
     * in the FDF field dictionary.
     *
     * @param ff The new value for the field flags.
     */
    public void setFieldFlags( int ff )
    {
        field.setInt( "Ff", ff );
    }

    /**
     * This will get the SetFf entry of the cos dictionary.  If it it not present then
     * this method will return null.
     *
     * @return The field flags.
     */
    public Integer getSetFieldFlags()
    {
        Integer retval = null;
        COSNumber ff = (COSNumber)field.getDictionaryObject( "SetFf" );
        if( ff != null )
        {
            retval = new Integer( ff.intValue() );
        }
        return retval;
    }

    /**
     * This will get the field flags that are associated with this field.  The SetFf entry
     * in the FDF field dictionary.
     *
     * @param ff The new value for the "set field flags".
     */
    public void setSetFieldFlags( Integer ff )
    {
        COSInteger value = null;
        if( ff != null )
        {
            value = COSInteger.get( ff );
        }
        field.setItem( "SetFf", value );
    }

    /**
     * This will get the field flags that are associated with this field.  The SetFf entry
     * in the FDF field dictionary.
     *
     * @param ff The new value for the "set field flags".
     */
    public void setSetFieldFlags( int ff )
    {
        field.setInt( "SetFf", ff );
    }

    /**
     * This will get the ClrFf entry of the cos dictionary.  If it it not present then
     * this method will return null.
     *
     * @return The field flags.
     */
    public Integer getClearFieldFlags()
    {
        Integer retval = null;
        COSNumber ff = (COSNumber)field.getDictionaryObject( "ClrFf" );
        if( ff != null )
        {
            retval = new Integer( ff.intValue() );
        }
        return retval;
    }

    /**
     * This will get the field flags that are associated with this field.  The ClrFf entry
     * in the FDF field dictionary.
     *
     * @param ff The new value for the "clear field flags".
     */
    public void setClearFieldFlags( Integer ff )
    {
        COSInteger value = null;
        if( ff != null )
        {
            value = COSInteger.get( ff );
        }
        field.setItem( "ClrFf", value );
    }

    /**
     * This will get the field flags that are associated with this field.  The ClrFf entry
     * in the FDF field dictionary.
     *
     * @param ff The new value for the "clear field flags".
     */
    public void setClearFieldFlags( int ff )
    {
        field.setInt( "ClrFf", ff );
    }

    /**
     * This will get the F entry of the cos dictionary.  If it it not present then
     * this method will return null.
     *
     * @return The widget field flags.
     */
    public Integer getWidgetFieldFlags()
    {
        Integer retval = null;
        COSNumber f = (COSNumber)field.getDictionaryObject( "F" );
        if( f != null )
        {
            retval = new Integer( f.intValue() );
        }
        return retval;
    }

    /**
     * This will get the widget field flags that are associated with this field.  The F entry
     * in the FDF field dictionary.
     *
     * @param f The new value for the field flags.
     */
    public void setWidgetFieldFlags( Integer f )
    {
        COSInteger value = null;
        if( f != null )
        {
            value = COSInteger.get( f );
        }
        field.setItem( "F", value );
    }

    /**
     * This will get the field flags that are associated with this field.  The F entry
     * in the FDF field dictionary.
     *
     * @param f The new value for the field flags.
     */
    public void setWidgetFieldFlags( int f )
    {
        field.setInt( "F", f );
    }

    /**
     * This will get the SetF entry of the cos dictionary.  If it it not present then
     * this method will return null.
     *
     * @return The field flags.
     */
    public Integer getSetWidgetFieldFlags()
    {
        Integer retval = null;
        COSNumber ff = (COSNumber)field.getDictionaryObject( "SetF" );
        if( ff != null )
        {
            retval = new Integer( ff.intValue() );
        }
        return retval;
    }

    /**
     * This will get the widget field flags that are associated with this field.  The SetF entry
     * in the FDF field dictionary.
     *
     * @param ff The new value for the "set widget field flags".
     */
    public void setSetWidgetFieldFlags( Integer ff )
    {
        COSInteger value = null;
        if( ff != null )
        {
            value = COSInteger.get( ff );
        }
        field.setItem( "SetF", value );
    }

    /**
     * This will get the widget field flags that are associated with this field.  The SetF entry
     * in the FDF field dictionary.
     *
     * @param ff The new value for the "set widget field flags".
     */
    public void setSetWidgetFieldFlags( int ff )
    {
        field.setInt( "SetF", ff );
    }

    /**
     * This will get the ClrF entry of the cos dictionary.  If it it not present then
     * this method will return null.
     *
     * @return The widget field flags.
     */
    public Integer getClearWidgetFieldFlags()
    {
        Integer retval = null;
        COSNumber ff = (COSNumber)field.getDictionaryObject( "ClrF" );
        if( ff != null )
        {
            retval = new Integer( ff.intValue() );
        }
        return retval;
    }

    /**
     * This will get the field flags that are associated with this field.  The ClrF entry
     * in the FDF field dictionary.
     *
     * @param ff The new value for the "clear widget field flags".
     */
    public void setClearWidgetFieldFlags( Integer ff )
    {
        COSInteger value = null;
        if( ff != null )
        {
            value = COSInteger.get( ff );
        }
        field.setItem( "ClrF", value );
    }

    /**
     * This will get the field flags that are associated with this field.  The ClrF entry
     * in the FDF field dictionary.
     *
     * @param ff The new value for the "clear field flags".
     */
    public void setClearWidgetFieldFlags( int ff )
    {
        field.setInt( "ClrF", ff );
    }

    /**
     * This will get the appearance dictionary that specifies the appearance of
     * a pushbutton field.
     *
     * @return The AP entry of this dictionary.
     */
    public PDAppearanceDictionary getAppearanceDictionary()
    {
        PDAppearanceDictionary retval = null;
        COSDictionary dict = (COSDictionary)field.getDictionaryObject( "AP" );
        if( dict != null )
        {
            retval = new PDAppearanceDictionary( dict );
        }
        return retval;
    }

    /**
     * This will set the appearance dictionary.
     *
     * @param ap The apperance dictionary.
     */
    public void setAppearanceDictionary( PDAppearanceDictionary ap )
    {
        field.setItem( "AP", ap );
    }

    /**
     * This will get named page references..
     *
     * @return The named page references.
     */
    public FDFNamedPageReference getAppearanceStreamReference()
    {
        FDFNamedPageReference retval = null;
        COSDictionary ref = (COSDictionary)field.getDictionaryObject( "APRef" );
        if( ref != null )
        {
            retval = new FDFNamedPageReference( ref );
        }
        return retval;
    }

    /**
     * This will set the named page references.
     *
     * @param ref The named page references.
     */
    public void setAppearanceStreamReference( FDFNamedPageReference ref )
    {
        field.setItem( "APRef", ref );
    }

    /**
     * This will get the icon fit that is associated with this field.
     *
     * @return The IF entry.
     */
    public FDFIconFit getIconFit()
    {
        FDFIconFit retval = null;
        COSDictionary dic = (COSDictionary)field.getDictionaryObject( "IF" );
        if( dic != null )
        {
            retval = new FDFIconFit( dic );
        }
        return retval;
    }

    /**
     * This will set the icon fit entry.
     *
     * @param fit The icon fit object.
     */
    public void setIconFit( FDFIconFit fit )
    {
        field.setItem( "IF", fit );
    }

    /**
     * This will return a list of options for a choice field.  The value in the
     * list will be 1 of 2 types.  java.lang.String or FDFOptionElement.
     *
     * @return A list of all options.
     */
    public List getOptions()
    {
        List retval = null;
        COSArray array = (COSArray)field.getDictionaryObject( "Opt" );
        if( array != null )
        {
            List objects = new ArrayList();
            for( int i=0; i<array.size(); i++ )
            {
                COSBase next = array.getObject( i );
                if( next instanceof COSString )
                {
                    objects.add( ((COSString)next).getString() );
                }
                else
                {
                    COSArray value = (COSArray)next;
                    objects.add( new FDFOptionElement( value ) );
                }
            }
            retval = new COSArrayList( objects, array );
        }
        return retval;
    }

    /**
     * This will set the options for the choice field.  The objects in the list
     * should either be java.lang.String or FDFOptionElement.
     *
     * @param options The options to set.
     */
    public void setOptions( List options )
    {
        COSArray value = COSArrayList.converterToCOSArray( options );
        field.setItem( "Opt", value );
    }

    /**
     * This will get the action that is associated with this field.
     *
     * @return The A entry in the field dictionary.
     */
    public PDAction getAction()
    {
        return PDActionFactory.createAction( (COSDictionary)field.getDictionaryObject( "A" ) );
    }

    /**
     * This will set the action that is associated with this field.
     *
     * @param a The new action.
     */
    public void setAction( PDAction a )
    {
        field.setItem( "A", a );
    }

    /**
     * This will get a list of additional actions that will get executed based
     * on events.
     *
     * @return The AA entry in this field dictionary.
     */
    public PDAdditionalActions getAdditionalActions()
    {
        PDAdditionalActions retval = null;
        COSDictionary dict = (COSDictionary)field.getDictionaryObject( "AA" );
        if( dict != null )
        {
            retval = new PDAdditionalActions( dict );
        }

        return retval;
    }

    /**
     * This will set the additional actions that are associated with this field.
     *
     * @param aa The additional actions.
     */
    public void setAdditionalActions( PDAdditionalActions aa )
    {
        field.setItem( "AA", aa );
    }

    /**
     * This will set the rich text that is associated with this field.
     *
     * @return The rich text XHTML stream.
     */
    public PDTextStream getRichText()
    {
        COSBase rv = field.getDictionaryObject( "RV" );
        return PDTextStream.createTextStream( rv );
    }

    /**
     * This will set the rich text value.
     *
     * @param rv The rich text value for the stream.
     */
    public void setRichText( PDTextStream rv )
    {
        field.setItem( "RV", rv );
    }
}
