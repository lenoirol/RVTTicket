const carousel=document.querySelector('[data-destination-carousel]');
if(carousel){
  const track=carousel.querySelector('.destination-slides'),slides=[...carousel.querySelectorAll('.destination-slide')],dots=[...carousel.querySelectorAll('[data-slide-index]')],pause=carousel.querySelector('[data-carousel-pause]');
  const reduced=matchMedia('(prefers-reduced-motion: reduce)');let index=0,timer=null,paused=reduced.matches,hovered=false,startX=null;
  function stop(){if(timer)clearInterval(timer);timer=null;}
  function schedule(){stop();if(!paused&&!hovered&&!document.hidden&&!carousel.contains(document.activeElement))timer=setInterval(()=>show(index+1),7000);}
  function show(next,manual=false){
    index=(next+slides.length)%slides.length;track.style.transform=`translateX(-${index*100}%)`;
    slides.forEach((slide,i)=>{slide.setAttribute('aria-hidden',String(i!==index));slide.inert=i!==index;});
    dots.forEach((dot,i)=>{dot.setAttribute('aria-pressed',String(i===index));});
    const status=carousel.querySelector('[data-carousel-status]');status.setAttribute('aria-live',manual?'polite':'off');status.textContent=`${index+1} / ${slides.length} · ${slides[index].dataset.destination}`;
  }
  function navigate(next){paused=true;pause.textContent='Tiếp tục';pause.setAttribute('aria-label','Tiếp tục trình chiếu điểm đến');pause.setAttribute('aria-pressed','true');show(next,true);schedule();}
  carousel.querySelector('[data-carousel-previous]').addEventListener('click',()=>navigate(index-1));
  carousel.querySelector('[data-carousel-next]').addEventListener('click',()=>navigate(index+1));
  dots.forEach(dot=>dot.addEventListener('click',()=>navigate(Number(dot.dataset.slideIndex))));
  pause.addEventListener('click',()=>{paused=!paused;pause.textContent=paused?'Tiếp tục':'Tạm dừng';pause.setAttribute('aria-label',paused?'Tiếp tục trình chiếu điểm đến':'Tạm dừng trình chiếu điểm đến');pause.setAttribute('aria-pressed',String(paused));schedule();});
  carousel.addEventListener('mouseenter',()=>{hovered=true;stop();});carousel.addEventListener('mouseleave',()=>{hovered=false;schedule();});
  carousel.addEventListener('focusin',stop);carousel.addEventListener('focusout',()=>queueMicrotask(schedule));
  carousel.addEventListener('keydown',event=>{if(event.key==='ArrowRight'||event.key==='ArrowLeft'){event.preventDefault();navigate(index+(event.key==='ArrowRight'?1:-1));}});
  carousel.addEventListener('pointerdown',event=>{if(!event.target.closest('button,a'))startX=event.clientX;});
  carousel.addEventListener('pointerup',event=>{if(startX!==null&&Math.abs(event.clientX-startX)>45)navigate(index+(event.clientX<startX?1:-1));startX=null;});carousel.addEventListener('pointercancel',()=>startX=null);
  document.addEventListener('visibilitychange',schedule);window.addEventListener('pagehide',stop);window.addEventListener('pageshow',schedule);
  reduced.addEventListener('change',()=>{if(reduced.matches){paused=true;pause.textContent='Tiếp tục';pause.setAttribute('aria-label','Tiếp tục trình chiếu điểm đến');pause.setAttribute('aria-pressed','true');}schedule();});
  if(paused){pause.textContent='Tiếp tục';pause.setAttribute('aria-label','Tiếp tục trình chiếu điểm đến');pause.setAttribute('aria-pressed','true');}show(0);schedule();
}
