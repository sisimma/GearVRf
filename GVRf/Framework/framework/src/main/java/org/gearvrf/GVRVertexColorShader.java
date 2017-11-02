package org.gearvrf;

import android.content.Context;

import org.gearvrf.utility.TextFile;

public class GVRVertexColorShader extends GVRShaderTemplate
{
    private static String fragTemplate = null;
    private static String vtxTemplate = null;

    public GVRVertexColorShader(GVRContext gvrcontext)
    {
        super("", 300);
        Context context = gvrcontext.getContext();
        fragTemplate = TextFile.readTextFile(context, R.raw.vcolor_fragment);
        vtxTemplate = TextFile.readTextFile(context, R.raw.vcolor_vertex);
        setSegment("FragmentTemplate", fragTemplate);
        setSegment("VertexTemplate", vtxTemplate);
    }
}
