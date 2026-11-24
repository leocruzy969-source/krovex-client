#version 330

uniform sampler2D InSampler;

layout(std140) uniform GradeConfig {
    vec4 Params;   // x saturation (1 = unchanged), y contrast (1), z warmth (-1 cool .. +1 warm), w shadows (-1 crush .. +1 lift)
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec3 c = texture(InSampler, texCoord).rgb;
    float saturation = Params.x, contrast = Params.y, warmth = Params.z, shadows = Params.w;
    float luma = dot(c, vec3(0.2126, 0.7152, 0.0722));
    float dark = 1.0 - smoothstep(0.0, 0.55, luma);          // 1 in the shadows, 0 in the lights
    c += shadows * 0.18 * dark;
    c = mix(vec3(luma), c, saturation);
    c = (c - 0.5) * contrast + 0.5;
    c *= vec3(1.0 + 0.14 * warmth, 1.0 + 0.03 * warmth, 1.0 - 0.14 * warmth);
    fragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}