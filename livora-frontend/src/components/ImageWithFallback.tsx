import React, { useState } from 'react';

interface ImageWithFallbackProps extends React.ImgHTMLAttributes<HTMLImageElement> {
  fallback: React.ReactNode;
}

const ImageWithFallback: React.FC<ImageWithFallbackProps> = ({ src, alt, fallback, style, ...props }) => {
  const [error, setError] = useState(false);

  if (error || !src) {
    return <>{fallback}</>;
  }

  return (
    <img
      src={src}
      alt={alt}
      style={style}
      onError={() => setError(true)}
      {...props}
    />
  );
};

export default ImageWithFallback;
