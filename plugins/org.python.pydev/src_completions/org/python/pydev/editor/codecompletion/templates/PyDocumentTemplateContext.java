/**
 * 
 */
package org.python.pydev.editor.codecompletion.templates;

import java.io.File;
import java.util.List;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.templates.DocumentTemplateContext;
import org.eclipse.jface.text.templates.Template;
import org.eclipse.jface.text.templates.TemplateBuffer;
import org.eclipse.jface.text.templates.TemplateContextType;
import org.eclipse.jface.text.templates.TemplateException;
import org.eclipse.jface.text.templates.TemplateTranslator;
import org.python.pydev.core.IGrammarVersionProvider;
import org.python.pydev.core.IIndentPrefs;
import org.python.pydev.core.IInterpreterInfo;
import org.python.pydev.core.IPythonNature;
import org.python.pydev.core.MisconfigurationException;
import org.python.pydev.core.docutils.PySelection;
import org.python.pydev.core.docutils.StringUtils;
import org.python.pydev.core.structure.FastStringBuffer;
import org.python.pydev.dltk.console.ui.IScriptConsoleViewer;
import org.python.pydev.editor.PyEdit;
import org.python.pydev.editor.autoedit.DefaultIndentPrefs;
import org.python.pydev.editor.codefolding.PySourceViewer;

/**
 * Makes a custom evaluation of the template buffer to be created (to put it in the correct indentation and 
 * change tabs to spaces -- if needed). 
 * 
 * @author Fabio
 */
public final class PyDocumentTemplateContext extends DocumentTemplateContext {
    
    private final String indentTo;
    private IIndentPrefs indentPrefs;
	public ITextViewer viewer; //May be null

    /**
     * Note that it's in the default context because it should be used on subclasses.
     */
    /* default */ PyDocumentTemplateContext(TemplateContextType type, IDocument document, int offset, int length, String indentTo, IIndentPrefs indentPrefs) {
        super(type, document, offset, length);
        this.indentTo = indentTo;
        this.indentPrefs = indentPrefs;
    }
    
    public PyDocumentTemplateContext(TemplateContextType type, IDocument document, int offset, int length, String indentTo, ITextViewer viewer) {
        this(type, document, offset, length, indentTo, getIndentPrefs(viewer));
        this.viewer = viewer;
    }
    
    
    public File getEditorFile() {
		if(this.viewer instanceof PySourceViewer){
			return ((PySourceViewer) this.viewer).getEdit().getEditorFile();
		}
		return new File("");
	}
    
    public int getGrammarVersion() {
    	//Other possibilities
        //org.eclipse.jface.text.source.SourceViewer (in compare)

    	if(this.viewer instanceof PySourceViewer){
    		try {
				IPythonNature nature = ((PySourceViewer) this.viewer).getEdit().getPythonNature();
				if(nature != null){
					return nature.getGrammarVersion();
				}
			} catch (MisconfigurationException e) {
			}
    	}
    	
    	if(this.viewer instanceof IScriptConsoleViewer){
    		//interactive console
    	    IScriptConsoleViewer v = (IScriptConsoleViewer) this.viewer;
    		IInterpreterInfo interpreterInfo = v.getInterpreterInfo();
    		if(interpreterInfo != null){
    			return interpreterInfo.getGrammarVersion();
    		}
    		
    	}
    	return IGrammarVersionProvider.LATEST_GRAMMAR_VERSION;
	}
    
    public String getModuleName() {
    	if(this.viewer instanceof PySourceViewer){
    		try {
    			PySourceViewer pyViewer = (PySourceViewer) this.viewer;
				PyEdit edit = pyViewer.getEdit();
				IPythonNature nature = edit.getPythonNature();
    			if(nature != null){
    				return nature.resolveModule(edit.getEditorFile());
    			}
    		} catch (MisconfigurationException e) {
    		}
    	}
    	return "";
	}

    /**
     * @return the indent preferences to be used.
     */
    private static IIndentPrefs getIndentPrefs(ITextViewer viewer) {
        if(viewer instanceof PySourceViewer){
        	PySourceViewer pyViewer = (PySourceViewer) viewer;
            return pyViewer.getEdit().getIndentPrefs();
        }else{
            return DefaultIndentPrefs.get();
        }
    }
    
    @Override
    public TemplateBuffer evaluate(Template template) throws BadLocationException, TemplateException {
        
        if (!canEvaluate(template)){
            return null;
        }
        
        
         String spacesIndentString = StringUtils.createSpaceString(indentPrefs.getTabWidth());        
        
        
        
        //indent to needed level and
        //replace any \t for the indentation string 
        String pattern = template.getPattern();
        List<String> splitted = StringUtils.splitInLines(pattern);
        
        boolean changed = false;
        if(indentPrefs.getUseSpaces(true)){
            if(pattern.indexOf("\t") != -1){
                template = createNewTemplate(template, StringUtils.replaceAll(pattern, "\t", spacesIndentString));
                changed = true;
            }
        }else{
            if(pattern.indexOf(spacesIndentString) != -1){
                FastStringBuffer newPattern = new FastStringBuffer();
                FastStringBuffer newTabsIndent = new FastStringBuffer();
                
                for(int i=0; i<splitted.size();i++){
                    String string = splitted.get(i);
                    
                    int spacesFound = 0;
                    while(string.length() > 0 && string.charAt(0) == ' '){
                        string = string.substring(1);
                        spacesFound += 1;
                    }
                    
                    int tabsToAdd = 0;
                    if(spacesFound > 0){
                        tabsToAdd = spacesFound / spacesIndentString.length();
                        if(spacesFound % spacesIndentString.length() != 0){
                            tabsToAdd += 1;
                        }
                        newTabsIndent.clear();
                        for(int j = 0; j< tabsToAdd; j++){
                            newTabsIndent.append("\t");
                        }
                        newPattern.append(newTabsIndent);
                    }
                    newPattern.append(string);
                }
                template = createNewTemplate(template, newPattern.toString());
                changed = true;
            }
        }
        
        //recreate it (if needed). 
        if(changed){
            pattern = template.getPattern();
            splitted = StringUtils.splitInLines(pattern);
        }
        
        String indentToStr = indentTo != null?indentTo:"";
        String endLineDelim = PySelection.getDelimiter(this.getDocument());
        
        int size = splitted.size();
        if(size > 0){
            
            FastStringBuffer buffer = new FastStringBuffer("", (pattern.length()+(size*2))+((size+1)*indentToStr.length()));
            for (int i=0; i<size;i++) { //we don't want to get the first line
                
                if(i != 0){
                    //the 1st line is not indented (that's where the user requested the completion -- others should be indented to it)
                    buffer.append(indentToStr);
                }
                
                String str = splitted.get(i);
                
                //we have to make the new line delimiter correct:
                //https://sourceforge.net/tracker/index.php?func=detail&aid=2019419&group_id=85796&atid=577329
                boolean hasNewLine = false;
                if(str.endsWith("\r") || str.endsWith("\n")){
                    hasNewLine = true;
                    if(str.endsWith("\r\n")){
                        str = str.substring(0, str.length()-2);
                    }else{
                        str = str.substring(0, str.length()-1);
                    }
                }
                buffer.append(str);
                if(hasNewLine){
                    buffer.append(endLineDelim);
                }
            }
            //just to change the pattern...
            template = createNewTemplate(template, buffer.toString());
        }
        
        TemplateTranslator translator= new TemplateTranslator();
        TemplateBuffer templateBuffer= translator.translate(template);

        getContextType().resolve(templateBuffer, this);

        return templateBuffer;
    }

    private Template createNewTemplate(Template template, String newString) {
        return new Template(template.getName(), template.getDescription(), template.getContextTypeId(), newString, template.isAutoInsertable());
    }
}